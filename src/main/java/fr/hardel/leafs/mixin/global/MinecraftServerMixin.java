package fr.hardel.leafs.mixin.global;

import fr.hardel.leafs.global.BarrierWindow;
import fr.hardel.leafs.global.GlobalServerAccess;
import fr.hardel.leafs.ticking.LeafsServerAccess;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The barrier window fires once per global tick, after the level ticks and before connections. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin implements GlobalServerAccess {

    @Unique
    private BarrierWindow leafs$barrierWindow;

    @Override
    public BarrierWindow leafs$barrierWindow() {
        if (leafs$barrierWindow == null) {
            leafs$barrierWindow = new BarrierWindow(((LeafsServerAccess) this).leafs$ticking().barrier());
        }

        return leafs$barrierWindow;
    }

    @Inject(method = "tickChildren", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;tickConnection()V"))
    private void leafs$runBarrierWindow(CallbackInfo callbackInfo) {
        this.leafs$barrierWindow().runGlobalPhase();
    }
}
