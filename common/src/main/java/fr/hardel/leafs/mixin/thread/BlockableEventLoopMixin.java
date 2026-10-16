package fr.hardel.leafs.mixin.thread;

import fr.hardel.leafs.thread.GameThreads;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockableEventLoop.class)
public abstract class BlockableEventLoopMixin {

    @Inject(method = "isSameThread", at = @At("HEAD"), cancellable = true)
    private void leafs$everyGameThreadIsTheServer(CallbackInfoReturnable<Boolean> callbackInfo) {
        if ((Object) this instanceof MinecraftServer server) {
            callbackInfo.setReturnValue(GameThreads.sameThreadAs(server.getRunningThread()));
        }
    }
}
