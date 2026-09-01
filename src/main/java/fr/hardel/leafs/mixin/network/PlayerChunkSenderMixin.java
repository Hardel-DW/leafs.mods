package fr.hardel.leafs.mixin.network;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.ConcurrentLongSet;
import fr.hardel.leafs.chunk.RegionChunkAccess;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The pending set takes writers from the marking thread and the sending region; the scalar state stays owner only. The send itself only takes chunks the sending region may serialize. */
@Mixin(PlayerChunkSender.class)
public abstract class PlayerChunkSenderMixin {

    @Mutable
    @Shadow
    @Final
    private LongSet pendingChunks;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$concurrentPendingSet(boolean memoryConnection, CallbackInfo callbackInfo) {
        this.pendingChunks = new ConcurrentLongSet();
    }

    /** The readiness test stays vanilla everywhere else, so a chunk in view is marked pending whoever owns it; only the batch skips what this region may not serialize. */
    @WrapOperation(method = "collectChunksToSend", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;getChunkToSend(J)Lnet/minecraft/world/level/chunk/LevelChunk;"))
    private LevelChunk leafs$onlySerializableChunks(ChunkMap chunkMap, long key, Operation<LevelChunk> original) {
        return RegionChunkAccess.sendable(chunkMap, key) ? original.call(chunkMap, key) : null;
    }
}
