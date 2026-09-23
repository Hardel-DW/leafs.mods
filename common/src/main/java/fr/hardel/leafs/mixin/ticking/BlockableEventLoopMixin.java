package fr.hardel.leafs.mixin.ticking;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.ticking.ChunkPumpAccess;
import fr.hardel.leafs.ticking.RegionBorrow;
import fr.hardel.leafs.ticking.RegionTickScheduler;
import fr.hardel.leafs.ticking.TickingManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockableEventLoop.class)
public abstract class BlockableEventLoopMixin {

    @Inject(method = "isSameThread", at = @At("HEAD"), cancellable = true)
    private void leafs$regionWorkerIsAGameThread(CallbackInfoReturnable<Boolean> callbackInfo) {
        if ((Object) this instanceof MinecraftServer && RegionTickScheduler.onWorker()) {
            callbackInfo.setReturnValue(true);
        }
    }

    @Inject(method = "pollTask", at = @At("HEAD"), cancellable = true)
    private void leafs$pumpDivertedTasks(CallbackInfoReturnable<Boolean> callbackInfo) {
        if ((Object) this instanceof MinecraftServer server && TickingManager.of(server).pumpDiverted()) {
            callbackInfo.setReturnValue(true);
        }
    }

    @WrapMethod(method = "doRunTask")
    private void leafs$lockDuringTheTask(Runnable task, Operation<Void> original) {
        if (RegionTickScheduler.onWorker() || !((Object) this instanceof MinecraftServer || (Object) this instanceof ChunkPumpAccess)) {
            original.call(task);
            return;
        }

        RegionBorrow.hold(_ -> original.call(task));
    }

    @Inject(method = "execute(Ljava/lang/Runnable;)V", at = @At("HEAD"), cancellable = true)
    private void leafs$divertOffThreadServerExecute(Runnable command, CallbackInfo callbackInfo) {
        if ((Object) this instanceof MinecraftServer server && TickingManager.of(server).divertExecute(command)) {
            callbackInfo.cancel();
        }
    }
}
