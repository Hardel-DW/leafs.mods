package fr.hardel.leafs.mixin.ticking;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.LeafsConfig;
import fr.hardel.leafs.metrics.TickStages;
import fr.hardel.leafs.metrics.StageTimings;
import fr.hardel.leafs.ticking.LeafsServerAccess;
import fr.hardel.leafs.ticking.TickingManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/** Hook only - the logic lives in ticking/TickingManager: each level tick runs through its region unit. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin implements LeafsServerAccess {

    @Unique
    private final TickingManager leafs$ticking = new TickingManager((MinecraftServer) (Object) this, LeafsConfig.get());

    @Override
    public TickingManager leafs$ticking() {
        return leafs$ticking;
    }

    @WrapOperation(method = "tickChildren", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;tick(Ljava/util/function/BooleanSupplier;)V"))
    private void leafs$tickLevelThroughRegionUnit(ServerLevel level, BooleanSupplier haveTime, Operation<Void> original) {
        leafs$ticking.tickLevel(level, () -> original.call(level, haveTime));
    }

    /** The global stage sample spans {@code tickServer}; an early pause-branch return still publishes at RETURN. */
    @Inject(method = "tickServer", at = @At("HEAD"))
    private void leafs$beginGlobalStages(BooleanSupplier haveTime, CallbackInfo callbackInfo) {
        long now = System.nanoTime();
        leafs$ticking.metrics().globalStages().beginTick(now);
        leafs$ticking.serialBudget().beginTick(now);
    }

    @Inject(method = {"tickChildren", "tickServer"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;tickConnection()V", shift = At.Shift.AFTER), require = 0)
    private void leafs$markConnectionsStage(CallbackInfo callbackInfo) {
        leafs$ticking.metrics().globalStages().mark(TickStages.globalConnections);
    }

    @Inject(method = "tickChildren", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;tick()V", shift = At.Shift.AFTER), require = 0)
    private void leafs$markPlayersStage(BooleanSupplier haveTime, CallbackInfo callbackInfo) {
        leafs$ticking.metrics().globalStages().mark(TickStages.globalPlayers);
    }

    @Inject(method = "tickServer", at = @At("RETURN"))
    private void leafs$endGlobalStages(BooleanSupplier haveTime, CallbackInfo callbackInfo) {
        StageTimings globalStages = leafs$ticking.metrics().globalStages();
        globalStages.mark(TickStages.globalAutosave);
        globalStages.endTick(System.nanoTime());
    }

    /** Vanilla drains its packet queue here every loop iteration, paused included; the stolen per-player queues must too. */
    @Inject(method = "processPacketsAndTick", at = @At("HEAD"))
    private void leafs$drainPlayerQueuesWhilePaused(boolean sprinting, CallbackInfo callbackInfo) {
        if (((MinecraftServer) (Object) this).isPaused()) {
            leafs$ticking.tickPausedNetwork();
        }
    }

    @Inject(method = "tickChildren", at = @At("TAIL"))
    private void leafs$quiesceLevels(BooleanSupplier haveTime, CallbackInfo callbackInfo) {
        StageTimings globalStages = leafs$ticking.metrics().globalStages();
        globalStages.mark(TickStages.globalSendChunks);
        leafs$ticking.quiesce();
        globalStages.mark(TickStages.globalQuiesce);
    }

    /** Before the worlds save: the pool stops so saves read settled state, then pending teleports place. */
    @Inject(method = "stopServer", at = @At("HEAD"))
    private void leafs$haltTicking(CallbackInfo callbackInfo) {
        leafs$ticking.haltTicking();
    }

    @Inject(method = "stopServer", at = @At("TAIL"))
    private void leafs$shutdownTicking(CallbackInfo callbackInfo) {
        leafs$ticking.shutdown();
    }
}
