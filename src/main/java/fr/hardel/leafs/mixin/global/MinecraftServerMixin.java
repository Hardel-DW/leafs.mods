package fr.hardel.leafs.mixin.global;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.global.SyncWindow;
import fr.hardel.leafs.global.GlobalServerAccess;
import fr.hardel.leafs.global.LeafsGameRules;
import fr.hardel.leafs.metrics.TickStages;
import fr.hardel.leafs.metrics.StageTimings;
import fr.hardel.leafs.metrics.DeferReason;
import fr.hardel.leafs.ticking.TickingManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The sync window, once per global tick after level ticks. Built at constructor tail because it needs ticking/'s barrier. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin implements GlobalServerAccess {

    @Unique
    private SyncWindow leafs$syncWindow;

    @Override
    public SyncWindow leafs$syncWindow() {
        return leafs$syncWindow;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$createSyncWindow(CallbackInfo callbackInfo) {
        TickingManager ticking = TickingManager.of((MinecraftServer) (Object) this);
        leafs$syncWindow = new SyncWindow(ticking.barrier(), ticking.metrics().barrier(), ticking.metrics().deferStats());
    }

    /** Also hooked in {@code tickServer}: its pause-when-empty branch skips {@code tickChildren}, yet diverted tasks must still drain. */
    @Inject(method = {"tickChildren", "tickServer"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;tickConnection()V"))
    private void leafs$runSyncWindow(CallbackInfo callbackInfo) {
        TickingManager ticking = TickingManager.of((MinecraftServer) (Object) this);
        StageTimings globalStages = ticking.metrics().globalStages();
        globalStages.mark(TickStages.globalLevels);
        ticking.globalScheduler().drain();
        globalStages.mark(TickStages.globalDrain);
        leafs$syncWindow.runGlobalPhase();
        globalStages.mark(TickStages.globalWindow);
    }

    /** Tick functions execute in the sync window. The tick_functions_work rule cuts the loop; reload stays. */
    @WrapOperation(method = "tickChildren", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/ServerFunctionManager;tick()V"))
    private void leafs$functionsIntoWindow(ServerFunctionManager manager, Operation<Void> original) {
        boolean tickFunctionsDue = !manager.ticking.isEmpty() && ((MinecraftServer) (Object) this).getGameRules().get(LeafsGameRules.tickFunctionsWork);
        if (manager.postReload || tickFunctionsDue) {
            leafs$syncWindow.enqueue(DeferReason.TICK_FUNCTIONS, manager::tick);
        }
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void leafs$runShutdownSyncWindow(CallbackInfo callbackInfo) {
        leafs$syncWindow.runShutdownPhase();
    }
}
