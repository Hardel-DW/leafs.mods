package fr.hardel.leafs.chunk.holder;

import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.owner.ChunkClaim;
import fr.hardel.leafs.chunk.owner.ChunkOwners;
import fr.hardel.leafs.chunk.owner.RegionInbox;
import fr.hardel.leafs.chunk.pool.ChunkPlacement;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.ticking.LevelRegions;
import net.minecraft.server.level.ChunkGenerationTask;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public record WaitReport(ServerLevel level, int chunkX, int chunkZ, ChunkStatus status, CompletableFuture<?> delivery, long startedNanos) {
    @Override
    public String toString() {
        LevelChunks chunks = LevelChunks.of(level);
        LevelRegions regions = LevelRegions.of(level);
        long key = ChunkPos.pack(chunkX, chunkZ);
        ChunkHolder holder = chunks.holders().table().get(key);
        return String.join(", ",
            "waiting for chunk [%d, %d] at %s, delivered %b".formatted(chunkX, chunkZ, status, delivery.isDone()),
            "loading level %d".formatted(chunks.graphs().loading().level(key)),
            holder == null ? "no holder" : holder(holder),
            queued(chunks.placement(), chunkX, chunkZ),
            owner(regions),
            "pool queued %d active %d".formatted(chunks.pool().queued(), chunks.pool().active()),
            "tickets %s".formatted(level.getChunkSource().ticketStorage.getTicketDebugString(key, false)));
    }

    public static String holder(ChunkHolder holder) {
        ChunkGenerationTask task = holder.task.get();
        return String.join(", ",
            "holder %s level %d, latest %s, started %s, generation refs %d".formatted(holder.getPos(), holder.getTicketLevel(), holder.getLatestStatus(), holder.startedWork.get(), holder.generationRefCount.get()),
            "pending %s".formatted(pending(holder)),
            task == null ? "no task" : task(task));
    }

    public static String queued(ChunkPlacement placement, int chunkX, int chunkZ) {
        int radius = ChunkLevel.RADIUS_AROUND_FULL_CHUNK;
        int around = 0;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                around += placement.queuedAt(chunkX + dx, chunkZ + dz);
            }
        }

        return "%d tasks queued within %d, %d on the chunk itself".formatted(around, radius, placement.queuedAt(chunkX, chunkZ));
    }

    public static @Nullable String taken(ChunkOwners owners, int chunkX, int chunkZ) {
        ChunkClaim claim = owners.claimAt(chunkX, chunkZ);
        return claim == null ? null : "taken by thread '%s' with %d queued".formatted(claim.holder().getName(), claim.mail().size());
    }

    static String task(ChunkGenerationTask task) {
        String head = "task to %s at %s, cancelled %b".formatted(task.targetStatus, task.scheduledStatus, task.markedForCancellation);
        ChunkStatus layer = task.scheduledStatus;
        if (layer == null) {
            return head;
        }

        List<String> stuck = new ArrayList<>();
        task.cache.forEach(member -> {
            CompletableFuture<?> future = member.futures.get(layer.getIndex());
            if (future != null && !future.isDone()) {
                stuck.add("%s latest %s started %s".formatted(member.getPos(), member.getLatestStatus(), member.startedWork.get()));
            }
        });
        
        if (stuck.isEmpty()) {
            return "%s, layer pending on 0".formatted(head);
        }

        return "%s, layer pending on %d, first %s".formatted(head, stuck.size(), stuck.getFirst());
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
