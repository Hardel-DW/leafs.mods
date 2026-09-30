package fr.hardel.leafs.chunk.holder;

import fr.hardel.leafs.chunk.LeafsTicketTypes;
import fr.hardel.leafs.chunk.level.ChunkLevels;
import fr.hardel.leafs.chunk.level.LevelListener;
import fr.hardel.leafs.chunk.owner.ChunkOwners;
import fr.hardel.leafs.chunk.owner.Work;
import fr.hardel.leafs.chunk.pool.ChunkNeed;
import fr.hardel.leafs.chunk.pool.ChunkPlacement;
import fr.hardel.leafs.metrics.MinuteCounter;
import fr.hardel.leafs.metrics.ServerMetrics;
import net.minecraft.server.level.ChunkGenerationTask;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class ChunkHolders {
    private final ChunkMap chunkMap;
    private final ChunkLevels loading;
    private final HolderTable table;
    private final PendingUnloads unloading;
    private final ChunkOwners owners;
    private final ChunkPlacement placement;
    private final TicketStorage tickets;
    private final GenerationSteps steps;
    private final Demands demands;
    private final MinuteCounter loads;
    private final MinuteCounter unloads;

    public ChunkHolders(ChunkMap chunkMap, ChunkLevels loading, HolderTable table, PendingUnloads unloading, ChunkOwners owners, ChunkPlacement placement, TicketStorage tickets, GenerationSteps steps, ServerMetrics metrics) {
        this.chunkMap = chunkMap;
        this.loading = loading;
        this.table = table;
        this.unloading = unloading;
        this.owners = owners;
        this.placement = placement;
        this.tickets = tickets;
        this.demands = new Demands(tickets, LeafsTicketTypes.demand);
        this.steps = steps;
        this.loads = metrics.chunkLoads();
        this.unloads = metrics.chunkUnloads();
    }

    public HolderTable table() {
        return table;
    }

    public Demands demands() {
        return demands;
    }

    public LevelListener publication() {
        return new Publication();
    }

    public record Demand(CompletableFuture<ChunkResult<ChunkAccess>> delivery, Runnable release, BooleanSupplier help) {
    }

    public Demand require(int chunkX, int chunkZ, ChunkStatus status) {
        long key = ChunkPos.pack(chunkX, chunkZ);
        int level = ChunkLevel.byStatus(status);
        demands.demand(key, level);
        CompletableFuture<ChunkResult<ChunkAccess>> delivery = settled(chunkX, chunkZ, () -> demanded(key, status).scheduleChunkGenerationTask(status, chunkMap));
        placement.expedite(ChunkNeed.of(ChunkPyramid.GENERATION_PYRAMID, chunkX, chunkZ, status));
        return new Demand(delivery, () -> demands.release(key, level), () -> {
            ChunkGenerationTask task = table.get(key).task.get();
            ChunkPyramid pyramid = task != null && task.needsGeneration ? ChunkPyramid.GENERATION_PYRAMID : ChunkPyramid.LOADING_PYRAMID;
            return placement.help(ChunkNeed.of(pyramid, chunkX, chunkZ, status), owners.holds(chunkX, chunkZ));
        });
    }

    private ChunkHolder demanded(long key, ChunkStatus status) {
        ChunkHolder holder = table.get(key);
        if (holder == null) {
            throw new IllegalStateException("Chunk %s demanded at %s has no holder: loading level %s, awaiting teardown %s, draining %s, tickets %s".formatted(
                ChunkPos.unpack(key), status, loading.level(key), unloading.containsKey(key), ChunkLevels.draining(), tickets.getTicketDebugString(key, false)));
        }

        return holder;
    }

    public <T> T settled(int chunkX, int chunkZ, Supplier<T> body) {
        return loading.settled(chunkX, chunkZ, publication(), body);
    }

    private static void queueLevelFollows(ChunkPos pos, IntSupplier oldLevel, int newLevel, IntConsumer setQueueLevel) {
        setQueueLevel.accept(newLevel);
    }

    private void unload(ChunkHolder holder) {
        ChunkPos pos = holder.getPos();
        unloads.increment();
        owners.submit(pos.x(), pos.z(), Work.CHUNK, () -> chunkMap.scheduleUnload(pos.pack(), holder));
    }

    private final class Publication implements LevelListener {
        private final List<ChunkHolder> holders = new ArrayList<>();

        @Override
        public void changed(long chunkKey, int oldLevel, int newLevel) {
            ChunkHolder holder = table.get(chunkKey);
            if (holder == null) {
                if (!ChunkLevel.isLoaded(newLevel)) {
                    return;
                }

                holder = unloading.remove(chunkKey);
                if (holder == null) {
                    holder = new ChunkHolder(ChunkPos.unpack(chunkKey), newLevel, chunkMap.level, chunkMap.lightEngine, ChunkHolders::queueLevelFollows, chunkMap);
                    loads.increment();
                }

                table.put(chunkKey, holder);
            }

            holder.setTicketLevel(newLevel);
            if (!ChunkLevel.isLoaded(newLevel)) {
                table.remove(chunkKey);
                unloading.put(chunkKey, holder);
            }

            holders.add(holder);
        }

        @Override
        public void published() {
            for (ChunkHolder holder : holders) {
                holder.updateHighestAllowedStatus(chunkMap);
                steps.cancelDisallowed(holder);
            }

            for (ChunkHolder holder : holders) {
                ChunkPos pos = holder.getPos();
                holder.updateFutures(chunkMap, owners.executor(pos.x(), pos.z()));
            }

            for (ChunkHolder holder : holders) {
                if (!ChunkLevel.isLoaded(holder.getTicketLevel())) {
                    unload(holder);
                }
            }

            holders.clear();
        }
    }
}
