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

/**
 * The barrier window fires once per global tick, after the level ticks and before connections, plus
 * one last time before the worlds are saved. It is built at the tail of the constructor rather than
 * from a field initialiser: initialisers of sibling mixins are merged right after {@code super()} in
 * an order we do not control, and this one needs ticking/'s barrier to exist already. Written once
 * on the constructing thread, before {@code spin} starts the server thread — every later reader is
 * behind that happens-before edge.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin implements GlobalServerAccess {

    @Unique
    private BarrierWindow leafs$barrierWindow;

    @Override
    public BarrierWindow leafs$barrierWindow() {
        return leafs$barrierWindow;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$createBarrierWindow(CallbackInfo callbackInfo) {
        leafs$barrierWindow = new BarrierWindow(((LeafsServerAccess) this).leafs$ticking().barrier());
    }

    @Inject(method = "tickChildren", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;tickConnection()V"))
    private void leafs$runBarrierWindow(CallbackInfo callbackInfo) {
        leafs$barrierWindow.runGlobalPhase();
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void leafs$runShutdownBarrierWindow(CallbackInfo callbackInfo) {
        leafs$barrierWindow.runShutdownPhase();
    }
}
