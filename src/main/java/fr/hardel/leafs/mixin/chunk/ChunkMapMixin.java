package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.sugar.Local;
import fr.hardel.leafs.chunk.RegionEntityTracking;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.ServerLevelRegionAccess;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BooleanSupplier;

/**
 * Hook only - the regionizer feed lives in ticking/LevelRegions; the M11b unload gate that will join
 * this class belongs to chunk/. These two sites are the only mutations of {@code updatingChunkMap},
 * and they alternate strictly per position, which is exactly the regionizer's add/remove contract.
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {

    @Inject(method = "updateChunkScheduling",
        at = @At(value = "FIELD", target = "Lnet/minecraft/server/level/ChunkMap;modified:Z", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER),
        require = 1, allow = 1)
    private void leafs$onChunkHolderCreated(long node, int level, ChunkHolder chunk, int oldLevel, CallbackInfoReturnable<ChunkHolder> callbackInfo) {
        leafs$regions().chunkHolderCreated(ChunkPos.getX(node), ChunkPos.getZ(node));
    }

    /**
     * Bound to the {@code scheduleUnload} call, never to its head: {@code scheduleUnload} re-invokes
     * itself when the save future was replaced, which would double-fire the removal.
     */
    @Inject(method = "processUnloads",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;scheduleUnload(JLnet/minecraft/server/level/ChunkHolder;)V"),
        require = 1, allow = 1)
    private void leafs$onChunkHolderDestroyed(BooleanSupplier haveTime, CallbackInfo callbackInfo, @Local(ordinal = 0) long pos) {
        leafs$regions().chunkHolderDestroyed(ChunkPos.getX(pos), ChunkPos.getZ(pos));
    }

    /** The #20b tracking split: the per-entity pass moved to the region bodies, the serial call keeps the player view diffs. */
    @Inject(method = "tick()V", at = @At("HEAD"), cancellable = true)
    private void leafs$serialTrackingHalf(CallbackInfo callbackInfo) {
        if (leafs$regions().body() == null) {
            return;
        }

        RegionEntityTracking.tickSerial((ChunkMap) (Object) this);
        callbackInfo.cancel();
    }

    @Unique
    private LevelRegions leafs$regions() {
        return ((ServerLevelRegionAccess) ((ChunkMap) (Object) this).level).leafs$regions();
    }
}
