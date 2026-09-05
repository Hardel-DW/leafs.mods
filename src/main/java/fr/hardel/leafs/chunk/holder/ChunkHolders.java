package fr.hardel.leafs.chunk.holder;

import fr.hardel.excess.ConcurrentLongSet;
import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.chunk.LeafsTicketTypes;
import fr.hardel.leafs.chunk.level.ChunkLevels;
import fr.hardel.leafs.chunk.level.LevelListener;
import fr.hardel.leafs.chunk.owner.ChunkOwners;
import fr.hardel.leafs.metrics.MinuteCounter;
import fr.hardel.leafs.metrics.ServerMetrics;
import it.unimi.dsi.fastutil.longs.LongIterator;
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
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Leafs' bookkeeping of vanilla's holders, fed by the loading graph: birth on a loaded level, level changes, unload on the owner. */
public final class ChunkHolders implements LevelListener {
    private final ChunkMap chunkMap;
    private final ChunkLevels loading;
    private final HolderTable table;
    private final PendingUnloads unloading;
    private final ChunkOwners owners;
    private final TicketStorage tickets;
    private final ConcurrentLongSet demands = new ConcurrentLongSet();
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

    @Override
    /** Vanilla's updateChunkScheduling: a loaded level revives the holder waiting for its teardown or makes a new one, an unloaded level sends it to the teardown. */
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
    /** Vanilla's two passes over the changed holders, then the unloads leave for their owner. */
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

    /** The chunk heads the pool until it lands; the request follows once its own ticket has settled, and its ticket leaves with the delivery. */
    public CompletableFuture<ChunkResult<ChunkAccess>> require(int chunkX, int chunkZ, ChunkStatus status) {
        long key = ChunkPos.pack(chunkX, chunkZ);
        Ticket demand = new Ticket(LeafsTicketTypes.demand, ChunkLevel.byStatus(status));
        demands.add(key);
        tickets.addTicket(key, demand);
        CompletableFuture<ChunkResult<ChunkAccess>> delivery = settled(chunkX, chunkZ, () -> table.get(key).scheduleChunkGenerationTask(status, chunkMap));
        owners.expedite(chunkX, chunkZ);
        delivery.whenComplete((_, _) -> {
            demands.remove(key);
            tickets.removeTicket(key, demand);
        });
        return delivery;
    }

    public boolean demanded(int chunkX, int chunkZ) {
        for (LongIterator demand = demands.iterator(); demand.hasNext(); ) {
            long key = demand.nextLong();
            int distance = Math.max(Math.abs(ChunkPos.getX(key) - chunkX), Math.abs(ChunkPos.getZ(key) - chunkZ));
            if (distance <= ChunkLevel.RADIUS_AROUND_FULL_CHUNK) {
                return true;
            }
        }

        return false;
    }

    public void settle(int chunkX, int chunkZ) {
        loading.settled(chunkX, chunkZ, this, () -> null);
    }

    public <T> T settled(int chunkX, int chunkZ, Supplier<T> body) {
        return loading.settled(chunkX, chunkZ, this, body);
    }

    /** Once the pool is done: the holders that left the table and never reached their teardown, by the status they stopped at, and the tasks still alive that hold them. */
    public void logWaitingTeardowns(String dimension) {
        List<ChunkHolder> waiting = unloading.snapshot();
        if (waiting.isEmpty()) {
            return;
        }

        List<ChunkHolder> tasked = new ArrayList<>();
        table.forEach((_, holder) -> {
            if (holder.task.get() != null) {
                tasked.add(holder);
            }
        });
        String byStatus = waiting.stream().collect(Collectors.groupingBy(holder -> String.valueOf(holder.getLatestStatus()), Collectors.counting())).toString();
        String samples = waiting.stream().limit(3).map(WaitReport::holder).collect(Collectors.joining("; "));
        String tasks = tasked.stream().limit(5).map(WaitReport::holder).collect(Collectors.joining("; "));
        Leafs.LOGGER.warn("{} holders of {} still wait for their teardown, by latest status {}: {}", waiting.size(), dimension, byStatus, samples);
        Leafs.LOGGER.warn("{} holders of {} still carry a generation task: {}", tasked.size(), dimension, tasks);
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
