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

/**
 * A queued light task only reaches the engine when something calls {@code tryScheduleUpdate}, and in
 * vanilla the single caller is the chunk source pump on the server thread. The INITIALIZE_LIGHT and
 * LIGHT steps are joined under the generation exclusion, so a chunk worker waits on that thread while
 * it holds an area, and the server thread waits on the region ticks every time it quiesces a level.
 * A region tick that wants the same area closes the cycle. The lane drives itself here: a task arms
 * its own drain from inside the queue, and a drain that leaves work behind arms the next one.
 */
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

    /**
     * The arming rides inside the queued task instead of following the submission, because the
     * dispatcher hands the task to the light lane later; an arming that ran here would look at a
     * queue its own entry has not reached yet, and the task would wait for the next caller.
     */
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
