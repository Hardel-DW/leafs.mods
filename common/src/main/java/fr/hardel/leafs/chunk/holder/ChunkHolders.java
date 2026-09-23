package fr.hardel.leafs.chunk.holder;

import fr.hardel.leafs.chunk.LeafsTicketTypes;
import fr.hardel.leafs.chunk.level.ChunkLevels;
import fr.hardel.leafs.chunk.level.LevelListener;
import fr.hardel.leafs.chunk.owner.ChunkOwners;
import fr.hardel.leafs.chunk.owner.Work;
import fr.hardel.leafs.metrics.MinuteCounter;
import fr.hardel.leafs.metrics.ServerMetrics;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class ChunkHolders implements LevelListener {
    private final ChunkMap chunkMap;
    private final ChunkLevels loading;
    private final HolderTable table;
    private final PendingUnloads unloading;
    private final ChunkOwners owners;
    private final TicketStorage tickets;
    private final GenerationSteps steps;
    private final Demands demands;
    private final MinuteCounter loads;
    private final MinuteCounter unloads;
    private final ThreadLocal<List<ChunkHolder>> batch = ThreadLocal.withInitial(ArrayList::new);

    public ChunkHolders(ChunkMap chunkMap, ChunkLevels loading, HolderTable table, PendingUnloads unloading, ChunkOwners owners, TicketStorage tickets, GenerationSteps steps, ServerMetrics metrics) {
        this.chunkMap = chunkMap;
        this.loading = loading;
        this.table = table;
        this.unloading = unloading;
        this.owners = owners;
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

        batch.get().add(holder);
    }

    @Override
    public void published() {
        List<ChunkHolder> changed = batch.get();
        for (ChunkHolder holder : changed) {
            holder.updateHighestAllowedStatus(chunkMap);
            steps.cancelDisallowed(holder);
        }

        for (ChunkHolder holder : changed) {
            ChunkPos pos = holder.getPos();
            holder.updateFutures(chunkMap, owners.executor(pos.x(), pos.z()));
        }

        for (ChunkHolder holder : changed) {
            if (!ChunkLevel.isLoaded(holder.getTicketLevel())) {
                unload(holder);
            }
        }

        changed.clear();
    }

    public record Demand(CompletableFuture<ChunkResult<ChunkAccess>> delivery, Runnable release) {
    }

    public Demand require(int chunkX, int chunkZ, ChunkStatus status) {
        long key = ChunkPos.pack(chunkX, chunkZ);
        int level = ChunkLevel.byStatus(status);
        demands.demand(key, level);
        CompletableFuture<ChunkResult<ChunkAccess>> delivery = settled(chunkX, chunkZ, () -> demanded(key, status).scheduleChunkGenerationTask(status, chunkMap));
        owners.expedite(chunkX, chunkZ);
        return new Demand(delivery, () -> demands.release(key, level));
    }

    private ChunkHolder demanded(long key, ChunkStatus status) {
        ChunkHolder holder = table.get(key);
        if (holder == null) {
            throw new IllegalStateException("Chunk " + ChunkPos.unpack(key) + " demanded at " + status + " has no holder: loading level " + loading.level(key) + ", awaiting teardown " + unloading.containsKey(key)
                + ", draining " + ChunkLevels.draining() + ", tickets " + tickets.getTicketDebugString(key, false));
        }

        return holder;
    }

    public <T> T settled(int chunkX, int chunkZ, Supplier<T> body) {
        return loading.settled(chunkX, chunkZ, this, body);
    }

    public boolean busy(long chunkKey) {
        ChunkHolder holder = table.get(chunkKey);
        return holder != null && !holder.isReadyForSaving();
    }

    private static void queueLevelFollows(ChunkPos pos, IntSupplier oldLevel, int newLevel, IntConsumer setQueueLevel) {
        setQueueLevel.accept(newLevel);
    }

    private void unload(ChunkHolder holder) {
        ChunkPos pos = holder.getPos();
        unloads.increment();
        owners.submit(pos.x(), pos.z(), Work.CHUNK, () -> chunkMap.scheduleUnload(pos.pack(), holder));
    }
}
