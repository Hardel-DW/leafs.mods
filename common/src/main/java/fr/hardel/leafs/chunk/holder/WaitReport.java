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
        long key = ChunkPos.pack(chunkX, chunkZ);
        ChunkHolder holder = chunks.holders().table().get(key);
        String holderState = holder == null ? "no holder" : holder(holder);
        String nearbyTasks = queued(chunks.placement(), chunkX, chunkZ);
        String owner = owner(LevelRegions.of(level));
        String tickets = level.getChunkSource().ticketStorage.getTicketDebugString(key, false);
        return "waiting for chunk [%d, %d] at %s, delivered %b, loading level %d, %s, %s, %s, pool queued %d active %d, tickets %s".formatted(
            chunkX, chunkZ, status, delivery.isDone(), chunks.graphs().loading().level(key), holderState, nearbyTasks, owner,
            chunks.pool().queued(), chunks.pool().active(), tickets);
    }

    public static String holder(ChunkHolder holder) {
        ChunkGenerationTask task = holder.task.get();
        String taskState = task == null ? "no task" : task(task);
        return "holder %s level %d, latest %s, started %s, generation refs %d, pending %s, %s".formatted(
            holder.getPos(), holder.getTicketLevel(), holder.getLatestStatus(), holder.startedWork.get(), holder.generationRefCount.get(), pending(holder), taskState);
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
            .filter(index -> holder.futures.get(index) instanceof CompletableFuture<?> future && !future.isDone())
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
