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

/** Barrier window: once per global tick after level ticks. Built at constructor tail because it needs ticking/'s barrier. */
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
    
    /**
     * A submitter must never block behind the barrier it feeds. Also hooked into {@code tickServer}:
     * its pause-when-empty branch skips {@code tickChildren}, yet a joining player's placement and other
     * diverted {@code MinecraftServer.execute} tasks land in the global phase and must still drain there.
     */
    @Inject(method = {"tickChildren", "tickServer"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;tickConnection()V"))
    private void leafs$runBarrierWindow(CallbackInfo callbackInfo) {
        ((LeafsServerAccess) this).leafs$ticking().globalScheduler().drain();
        leafs$barrierWindow.runGlobalPhase();
    }

    /** Tick functions execute in the barrier window. The tick_functions_work rule cuts the loop; reload stays. */
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
