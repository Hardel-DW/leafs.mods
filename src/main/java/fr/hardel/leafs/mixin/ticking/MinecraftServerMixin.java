package fr.hardel.leafs.mixin.ticking;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.LeafsConfig;
import fr.hardel.leafs.ticking.LeafsServerAccess;
import fr.hardel.leafs.ticking.LevelOwnership;
import fr.hardel.leafs.ticking.ServerLevelRegionAccess;
import fr.hardel.leafs.ticking.TickingManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
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

    /** Vanilla drains its packet queue here every loop iteration, paused included; the stolen per-player queues must too. */
    @Inject(method = "processPacketsAndTick", at = @At("HEAD"))
    private void leafs$drainPlayerQueuesWhilePaused(boolean sprinting, CallbackInfo callbackInfo) {
        if (((MinecraftServer) (Object) this).isPaused()) {
            leafs$ticking.tickPausedNetwork();
        }
    }

    /** The idle pump TRIES the level's exclusion: a level whose regions are mid-tick is skipped this round. */
    @WrapOperation(method = "pollTaskInternal", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;pollTask()Z"))
    private boolean leafs$pumpOnlyWhenLevelSerial(ServerChunkCache chunkSource, Operation<Boolean> original) {
        LevelOwnership ownership = ((ServerLevelRegionAccess) chunkSource.level).leafs$regions().ownership();
        if (!ownership.tryEnterLevelSerial()) {
            return false;
        }

        try {
            return original.call(chunkSource);
        } finally {
            ownership.exitLevelSerial();
        }
    }

    @Inject(method = "tickChildren", at = @At("TAIL"))
    private void leafs$quiesceLevels(BooleanSupplier haveTime, CallbackInfo callbackInfo) {
        leafs$ticking.quiesce();
    }

    /** Before the worlds save: the pool stops so saves read settled state, then pending teleports place. */
    @Inject(method = "stopServer", at = @At("HEAD"))
    private void leafs$haltTicking(CallbackInfo callbackInfo) {
        leafs$ticking.haltTicking((MinecraftServer) (Object) this);
    }

    @Inject(method = "stopServer", at = @At("TAIL"))
    private void leafs$shutdownTicking(CallbackInfo callbackInfo) {
        leafs$ticking.shutdown((MinecraftServer) (Object) this);
    }
}
