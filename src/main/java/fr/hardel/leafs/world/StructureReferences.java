package fr.hardel.leafs.world;

import fr.hardel.leafs.chunk.ChunkDemands;
import fr.hardel.leafs.chunk.RegionChunkAccess;
import fr.hardel.leafs.chunk.core.ChunkScheduling;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.concurrent.CompletableFuture;

// The reference write of a located structure needs the live start: the chunk is demanded, and the write runs on the
// owning thread at delivery. A delivery that fails, or a shutdown in between, loses one reference, never a position.
public final class StructureReferences {

    private StructureReferences() {
    }

    public static void writeAtDelivery(ServerLevel level, StructureManager structureManager, ChunkPos chunkPos, Structure structure) {
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        ChunkScheduling scheduling = RegionChunkAccess.scheduling(chunkMap);
        Runnable write = () -> writeReference(chunkMap, structureManager, chunkPos, structure);
        CompletableFuture<?> readiness = ChunkDemands.demand(chunkMap, ChunkStatus.STRUCTURE_STARTS, LongList.of(chunkPos.pack()));
        if (readiness == null) {
            scheduling.runOnOwner(chunkPos.x(), chunkPos.z(), write);
            return;
        }

        readiness.whenComplete((result, failure) -> scheduling.runOnOwner(chunkPos.x(), chunkPos.z(), write));
    }

    private static void writeReference(ChunkMap chunkMap, StructureManager structureManager, ChunkPos chunkPos, Structure structure) {
        ChunkHolder holder = chunkMap.getVisibleChunkIfPresent(chunkPos.pack());
        ChunkAccess chunk = holder == null ? null : holder.getChunkIfPresent(ChunkStatus.STRUCTURE_STARTS);
        if (chunk == null) {
            return;
        }

        StructureStart start = structureManager.getStartForStructure(SectionPos.bottomOf(chunk), structure, chunk);
        if (start != null && start.canBeReferenced()) {
            structureManager.addReference(start);
        }
    }
}
