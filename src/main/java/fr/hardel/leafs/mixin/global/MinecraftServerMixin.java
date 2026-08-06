package fr.hardel.leafs.mixin.global;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.global.BarrierWindow;
import fr.hardel.leafs.global.GlobalServerAccess;
import fr.hardel.leafs.global.LeafsGameRules;
import fr.hardel.leafs.global.WindowPressure;
import fr.hardel.leafs.ticking.LeafsServerAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionManager;
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
 * on the constructing thread, before {@code spin} starts the server thread - every later reader is
 * behind that happens-before edge.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin implements GlobalServerAccess {

    @Unique
    private BarrierWindow leafs$barrierWindow;

    @Unique
    private WindowPressure leafs$windowPressure;

    @Override
    public BarrierWindow leafs$barrierWindow() {
        return leafs$barrierWindow;
    }

    @Override
    public WindowPressure leafs$windowPressure() {
        return leafs$windowPressure;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$createBarrierWindow(CallbackInfo callbackInfo) {
        leafs$barrierWindow = new BarrierWindow(((LeafsServerAccess) this).leafs$ticking().barrier());
        leafs$windowPressure = new WindowPressure();
    }
    
    // Diverted execute tasks drain before the window raises: a submitter must never block behind the barrier it feeds.
    @Inject(method = "tickChildren", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;tickConnection()V"))
    private void leafs$runBarrierWindow(CallbackInfo callbackInfo) {
        ((LeafsServerAccess) this).leafs$ticking().globalScheduler().drain();
        leafs$barrierWindow.runGlobalPhase();
    }

    /**
     * The #24 wrap: {@code #tick} functions execute in this tick's window instead of before the
     * level ticks (Compromise #4). An idle manager, nothing in {@code #tick} and no reload to
     * drain, enqueues nothing, so a server without per-tick functions never raises the barrier.
     * The {@code leafs:tick_functions_work} rule cuts the {@code #tick} loop the same way; the
     * reload drain stays, so {@code #load} always runs and one vanilla tick unit rides with it.
     */
    @WrapOperation(method = "tickChildren", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/ServerFunctionManager;tick()V"))
    private void leafs$functionsIntoWindow(ServerFunctionManager manager, Operation<Void> original) {
        boolean tickFunctionsDue = !manager.ticking.isEmpty() && ((MinecraftServer) (Object) this).getGameRules().get(LeafsGameRules.tickFunctionsWork);
        if (manager.postReload || tickFunctionsDue) {
            leafs$barrierWindow.enqueue(manager::tick);
        }
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void leafs$runShutdownBarrierWindow(CallbackInfo callbackInfo) {
        leafs$barrierWindow.runShutdownPhase();
    }
}
