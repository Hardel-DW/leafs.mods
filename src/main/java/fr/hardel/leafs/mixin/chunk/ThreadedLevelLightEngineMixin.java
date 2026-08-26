package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.server.level.ChunkTaskDispatcher;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.util.thread.ConsecutiveExecutor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.IntSupplier;

/** Vanilla only drains light from the server-thread pump, a cycle with the generation exclusion. The lane drives itself: each task arms its own drain. */
@Mixin(ThreadedLevelLightEngine.class)
public abstract class ThreadedLevelLightEngineMixin {

    @Shadow
    @Final
    private ObjectList<?> lightTasks;

    @Shadow
    @Final
    private ConsecutiveExecutor consecutiveExecutor;

    @Shadow
    public abstract void tryScheduleUpdate();

    /** The arming rides inside the task: armed at submission it would look at a queue its entry has not reached yet. */
    @WrapOperation(method = "addTask(IILjava/util/function/IntSupplier;Lnet/minecraft/server/level/ThreadedLevelLightEngine$TaskType;Ljava/lang/Runnable;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkTaskDispatcher;submit(Ljava/lang/Runnable;JLjava/util/function/IntSupplier;)V"))
    private void leafs$armDrainFromTheQueue(ChunkTaskDispatcher dispatcher, Runnable queued, long pos, IntSupplier level, Operation<Void> original) {
        original.call(dispatcher, (Runnable) () -> {
            queued.run();
            tryScheduleUpdate();
        }, pos, level);
    }

    /** One drain covers a thousand tasks; past that the leftovers would wait for the pump again. */
    @Inject(method = "runUpdate", at = @At("RETURN"))
    private void leafs$armTheNextDrain(CallbackInfo callbackInfo) {
        if (!lightTasks.isEmpty()) {
            consecutiveExecutor.schedule(this::tryScheduleUpdate);
        }
    }
}
