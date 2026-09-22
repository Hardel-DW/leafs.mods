package fr.hardel.leafs.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.chunk.LevelChunks;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = ServerChunkCache.class, priority = 1100)
public abstract class LoadingChunkShim {
    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    @Final
    public ChunkMap chunkMap;

    @WrapMethod(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;")
    private ChunkAccess leafs$chunkUnderPublication(int x, int z, ChunkStatus status, boolean loadOrGenerate, Operation<ChunkAccess> original) {
        LevelChunk publishing = leafs$publishing(x, z);
        return publishing != null ? publishing : original.call(x, z, status, loadOrGenerate);
    }

    @WrapMethod(method = "getChunkNow(II)Lnet/minecraft/world/level/chunk/LevelChunk;")
    private LevelChunk leafs$chunkNowUnderPublication(int x, int z, Operation<LevelChunk> original) {
        LevelChunk publishing = leafs$publishing(x, z);
        return publishing != null ? publishing : original.call(x, z);
    }

    @Unique
    private LevelChunk leafs$publishing(int x, int z) {
        ChunkHolder holder = chunkMap.getVisibleChunkIfPresent(ChunkPos.pack(x, z));
        if (holder == null || holder.currentlyLoading == null) {
            return null;
        }

        return LevelChunks.of(level).owners().holds(x, z) ? holder.currentlyLoading : null;
    }
}
