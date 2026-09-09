package fr.hardel.leafs.world;

import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.RegionChunkAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Vanilla's block event set, cut per chunk. An event keeps the level-wide sequence it arrived with, so a region replays vanilla's FIFO across its chunks, and it leaves its set only as it runs. */
public final class ChunkBlockEvents {
    private static final Comparator<Pending> ORDER = Comparator.comparingLong(Pending::sequence);

    private record Pending(ChunkBlockEvents from, long sequence, BlockEventData event) {
        boolean take() {
            return from.events.remove(event, sequence);
        }
    }

    private final Map<BlockEventData, Long> events = new LinkedHashMap<>();

    /** Vanilla's blockEvent: the event joins its chunk's set on the chunk's owner. False for an unloaded position, which keeps vanilla's level set. */
    public static boolean post(ServerLevel level, BlockEventData event, long sequence) {
        int chunkX = SectionPos.blockToSectionCoord(event.pos().getX());
        int chunkZ = SectionPos.blockToSectionCoord(event.pos().getZ());
        if (RegionChunkAccess.levelChunkOrNull(level.getChunkSource().chunkMap, chunkX, chunkZ) == null) {
            return false;
        }

        write(level, chunkX, chunkZ, set -> set.add(event, sequence));
        return true;
    }

    /** Vanilla's clearBlockEvents over the loaded chunks of the box, each on its owner. */
    public static void clearArea(ServerLevel level, BoundingBox area) {
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        for (int chunkX = SectionPos.blockToSectionCoord(area.minX()); chunkX <= SectionPos.blockToSectionCoord(area.maxX()); chunkX++) {
            for (int chunkZ = SectionPos.blockToSectionCoord(area.minZ()); chunkZ <= SectionPos.blockToSectionCoord(area.maxZ()); chunkZ++) {
                if (RegionChunkAccess.levelChunkOrNull(chunkMap, chunkX, chunkZ) != null) {
                    write(level, chunkX, chunkZ, set -> set.removeInside(area));
                }
            }
        }
    }

    private static void write(ServerLevel level, int chunkX, int chunkZ, Consumer<ChunkBlockEvents> write) {
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        LevelChunks.of(level).owners().route(chunkX, chunkZ, () -> {
            LevelChunk chunk = RegionChunkAccess.levelChunkOrNull(chunkMap, chunkX, chunkZ);
            if (chunk != null) {
                write.accept(of(chunk));
            }
        });
    }

    /** Vanilla's runBlockEvents over the sets of one region's ticking chunks: sequence order across chunks, an event leaves its set as it runs, cascades replay until nothing is left. */
    public static void runAll(List<ChunkBlockEvents> sets, Consumer<BlockEventData> runner) {
        List<Pending> batch = new ArrayList<>();
        do {
            batch.clear();
            for (ChunkBlockEvents set : sets) {
                set.events.forEach((event, sequence) -> batch.add(new Pending(set, sequence, event)));
            }

            batch.sort(ORDER);
            for (Pending pending : batch) {
                if (pending.take()) {
                    runner.accept(pending.event());
                }
            }
        } while (!batch.isEmpty());
    }

    private static ChunkBlockEvents of(LevelChunk chunk) {
        return ((ChunkTickAccess) chunk).leafs$blockEvents();
    }

    /** Same event twice in a tick collapses onto the first, as vanilla's set does. */
    public void add(BlockEventData event, long sequence) {
        events.putIfAbsent(event, sequence);
    }

    public void removeInside(BoundingBox area) {
        events.keySet().removeIf(event -> area.isInside(event.pos()));
    }
}
