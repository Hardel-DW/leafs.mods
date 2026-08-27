package fr.hardel.leafs.chunk;

import fr.hardel.leafs.ownership.OwnershipViolationException;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiSection;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.concurrent.CompletableFuture;

// Batch residency: enumerate the chunks an area operation touches, demand the absent ones in one pass; the single ABSENT refusal carries the readiness that replays the work once loaded.
public final class AreaPreload {

    // createPortal probes a 16-block spiral and writes a frame a few blocks wider; chunk radius 2 covers both.
    private static final int PORTAL_WRITE_CHUNK_RADIUS = 2;

    private AreaPreload() {
    }

    // Mirror of PoiManager.ensureLoadedAndValid without the forced load; an absent candidate is unmarked again so a refused pass never buries the chunk as already checked.
    public static void ensurePoiSquare(PoiManager poi, ServerLevel level, BlockPos center, int radius) {
        LongList missing = new LongArrayList();
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        SectionPos.aroundChunk(ChunkPos.containing(center), Math.floorDiv(radius, 16),
                poi.levelHeightAccessor.getMinSectionY(), poi.levelHeightAccessor.getMaxSectionY())
            .filter(section -> !poi.getOrLoad(section.asLong()).map(PoiSection::isValid).orElse(false))
            .map(SectionPos::chunk)
            .filter(chunk -> poi.loadedChunks.add(chunk.pack()))
            .forEach(chunk -> {
                if (!present(chunkMap, chunk.pack(), ChunkStatus.EMPTY)) {
                    poi.loadedChunks.remove(chunk.pack());
                    missing.add(chunk.pack());
                }
            });

        refuseIfMissing(chunkMap, ChunkStatus.EMPTY, missing, "POI square around " + center);
    }

    // The write square of createPortal, resident at FULL before a single block is placed: no partial frame.
    public static void ensurePortalWriteSquare(ServerLevel level, BlockPos origin) {
        if (!DegradedChunkReads.active() && RegionChunkAccess.scheduling(level.getChunkSource().chunkMap).mayLoadSynchronously()) {
            return;
        }

        LongList missing = new LongArrayList();
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        ChunkPos center = ChunkPos.containing(origin);
        for (int chunkX = center.x() - PORTAL_WRITE_CHUNK_RADIUS; chunkX <= center.x() + PORTAL_WRITE_CHUNK_RADIUS; chunkX++) {
            for (int chunkZ = center.z() - PORTAL_WRITE_CHUNK_RADIUS; chunkZ <= center.z() + PORTAL_WRITE_CHUNK_RADIUS; chunkZ++) {
                long position = ChunkPos.pack(chunkX, chunkZ);
                if (!present(chunkMap, position, ChunkStatus.FULL)) {
                    missing.add(position);
                }
            }
        }

        refuseIfMissing(chunkMap, ChunkStatus.FULL, missing, "portal write square around " + origin);
    }

    // A search that loads chunks on its own future: the server thread waits as vanilla, a region refuses and the deferred work replays when it completes.
    public static void awaitOrRefuse(ServerLevel level, CompletableFuture<?> search, String what) {
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        if (!DegradedChunkReads.active() && RegionChunkAccess.scheduling(chunkMap).mayLoadSynchronously()) {
            level.getServer().managedBlock(search::isDone);
            return;
        }

        if (!search.isDone()) {
            refuse(chunkMap, search, "the " + what + " has not completed: this thread cannot block on it");
        }
    }

    private static boolean present(ChunkMap chunkMap, long position, ChunkStatus status) {
        ChunkHolder holder = chunkMap.getVisibleChunkIfPresent(position);
        return holder != null && holder.getChunkIfPresent(status) != null;
    }

    private static void refuseIfMissing(ChunkMap chunkMap, ChunkStatus status, LongList missing, String area) {
        if (missing.isEmpty()) {
            return;
        }

        refuse(chunkMap, ChunkDemands.demand(chunkMap, status, missing), missing.size() + " chunk(s) of the " + area + " not present at " + status + ": demanded in one pass, this thread cannot sync-load them");
    }

    private static void refuse(ChunkMap chunkMap, CompletableFuture<?> readiness, String message) {
        RegionChunkAccess.scheduling(chunkMap).deferStats().countRefusal(OwnershipViolationException.Kind.ABSENT, RegionChunkAccess.sourceOfCurrentThread());
        throw new OwnershipViolationException(OwnershipViolationException.Kind.ABSENT, message, readiness);
    }
}
