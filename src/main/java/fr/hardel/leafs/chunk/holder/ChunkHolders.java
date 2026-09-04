package fr.hardel.leafs.chunk.holder;

import fr.hardel.leafs.chunk.LeafsTicketTypes;
import fr.hardel.leafs.chunk.level.ChunkLevels;
import fr.hardel.leafs.chunk.level.LevelListener;
import fr.hardel.leafs.chunk.owner.ChunkOwners;
import fr.hardel.leafs.metrics.MinuteCounter;
import fr.hardel.leafs.metrics.ServerMetrics;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Leafs' bookkeeping of vanilla's holders, fed by the loading graph: birth on a loaded level, level changes, unload on the owner. */
public final class ChunkHolders implements LevelListener {
    private final ChunkMap chunkMap;
    private final ChunkLevels loading;
    private final HolderTable table;
    private final PendingUnloads unloading;
    private final ChunkOwners owners;
    private final TicketStorage tickets;
    private final MinuteCounter loads;
    private final MinuteCounter unloads;
    private final ThreadLocal<List<ChunkHolder>> batch = ThreadLocal.withInitial(ArrayList::new);

    public ChunkHolders(ChunkMap chunkMap, ChunkLevels loading, HolderTable table, PendingUnloads unloading, ChunkOwners owners, TicketStorage tickets, ServerMetrics metrics) {
        this.chunkMap = chunkMap;
        this.loading = loading;
        this.table = table;
        this.unloading = unloading;
        this.owners = owners;
        this.tickets = tickets;
        this.loads = metrics.chunkLoads();
        this.unloads = metrics.chunkUnloads();
    }

    public HolderTable table() {
        return table;
    }

    /** Vanilla's updateChunkScheduling: a loaded level revives the holder waiting for its teardown or makes a new one, an unloaded level sends it to the teardown. */
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

    /** Vanilla's two passes over the changed holders, then the unloads leave for their owner. */
    @Override
    public void published() {
        List<ChunkHolder> changed = batch.get();
        for (ChunkHolder holder : changed) {
            holder.updateHighestAllowedStatus(chunkMap);
        }

        for (ChunkHolder holder : changed) {
            updateFutures(holder);
        }

        for (ChunkHolder holder : changed) {
            if (!ChunkLevel.isLoaded(holder.getTicketLevel())) {
                unload(holder);
            }
        }

        changed.clear();
    }

    /** A status required by a waiting thread: the ticket makes the holder, the request follows under the drain's locks. */
    public CompletableFuture<ChunkResult<ChunkAccess>> require(int chunkX, int chunkZ, ChunkStatus status) {
        long key = ChunkPos.pack(chunkX, chunkZ);
        tickets.addTicket(key, new Ticket(LeafsTicketTypes.demand, ChunkLevel.byStatus(status)));
        return loading.locked(chunkX, chunkZ, () -> table.get(key).scheduleChunkGenerationTask(status, chunkMap));
    }

    /** Generation or a promotion in flight: vanilla's ticket countdown pauses on it. */
    public boolean busy(long chunkKey) {
        ChunkHolder holder = table.get(chunkKey);
        return holder != null && !holder.isReadyForSaving();
    }

    private static void queueLevelFollows(ChunkPos pos, IntSupplier oldLevel, int newLevel, IntConsumer setQueueLevel) {
        setQueueLevel.accept(newLevel);
    }

    private void updateFutures(ChunkHolder holder) {
        ChunkPos pos = holder.getPos();
        holder.updateFutures(chunkMap, owners.executor(pos.x(), pos.z()));
    }

    private void unload(ChunkHolder holder) {
        ChunkPos pos = holder.getPos();
        unloads.increment();
        owners.submit(pos.x(), pos.z(), () -> chunkMap.scheduleUnload(pos.pack(), holder));
    }
}
