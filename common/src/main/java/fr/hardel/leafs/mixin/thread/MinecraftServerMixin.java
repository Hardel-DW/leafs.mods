package fr.hardel.leafs.mixin.thread;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import fr.hardel.leafs.thread.GameThreads;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Shadow
    private int tickCount;

    @Inject(method = "runServer", at = @At("HEAD"))
    private void leafs$bindTheServerThread(CallbackInfo callbackInfo) {
        GameThreads.bindServer(() -> this.tickCount);
    }

    @ModifyReturnValue(method = "getTickCount", at = @At("RETURN"))
    private int leafs$tickOfTheWork(int serverTick) {
        return GameThreads.tickCount(serverTick);
    }
}
