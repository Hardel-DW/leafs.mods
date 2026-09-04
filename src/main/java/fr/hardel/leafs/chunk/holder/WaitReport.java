package fr.hardel.leafs.chunk.holder;

import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.owner.RegionInbox;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.ticking.LevelRegions;
import net.minecraft.server.level.ChunkGenerationTask;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** What a thread waits for, for a stall report. */
record WaitReport(ServerLevel level, int chunkX, int chunkZ, ChunkStatus status, CompletableFuture<?> delivery) {
    @Override
    public String toString() {
        LevelChunks chunks = LevelChunks.of(level);
        LevelRegions regions = LevelRegions.of(level);
        long key = ChunkPos.pack(chunkX, chunkZ);
        ChunkHolder holder = chunks.holders().table().get(key);
        return String.join(", ",
            "waiting for chunk [%d, %d] at %s, delivered %b".formatted(chunkX, chunkZ, status, delivery.isDone()),
            "loading level " + chunks.graphs().loading().level(key),
            holder == null ? "no holder" : holder(holder),
            chunks.steps().describeQueued(chunkX, chunkZ),
            owner(regions),
            "pool queued %d active %d".formatted(chunks.pool().queued(), chunks.pool().active()),
            "tickets " + level.getChunkSource().ticketStorage.getTicketDebugString(key, false));
    }

    private static String holder(ChunkHolder holder) {
        ChunkGenerationTask task = holder.task.get();
        return String.join(", ",
            "holder level %d, latest %s, started %s".formatted(holder.getTicketLevel(), holder.getLatestStatus(), holder.startedWork.get()),
            "pending " + pending(holder),
            task == null ? "no task" : "task to %s at %s, cancelled %b".formatted(task.targetStatus, task.scheduledStatus, task.markedForCancellation));
    }

    private static String pending(ChunkHolder holder) {
        List<ChunkStatus> statuses = ChunkStatus.getStatusList();
        String names = IntStream.range(0, holder.futures.length())
            .filter(index -> holder.futures.get(index) != null && !holder.futures.get(index).isDone())
            .mapToObj(index -> statuses.get(index).toString())
            .collect(Collectors.joining(" "));
        return names.isEmpty() ? "none" : names;
    }

    private String owner(LevelRegions regions) {
        Region<?> region = regions.regionizer().regionAt(chunkX, chunkZ);
        RegionInbox inbox = regions.inboxAt(chunkX, chunkZ);
        return region == null ? "owner pool" : "owner region #%d with %d queued".formatted(region.id(), inbox == null ? 0 : inbox.size());
    }
}
