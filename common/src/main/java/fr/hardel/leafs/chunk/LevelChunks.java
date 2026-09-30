package fr.hardel.leafs.chunk;

import fr.hardel.leafs.chunk.disk.ChunkWrites;
import fr.hardel.leafs.chunk.holder.ChunkHolders;
import fr.hardel.leafs.chunk.holder.GenerationSteps;
import fr.hardel.leafs.chunk.holder.HolderTable;
import fr.hardel.leafs.chunk.holder.PendingUnloads;
import fr.hardel.leafs.chunk.owner.ChunkOwners;
import fr.hardel.leafs.chunk.owner.UnownedSweep;
import fr.hardel.leafs.chunk.pool.ChunkPlacement;
import fr.hardel.leafs.chunk.pool.ChunkPool;
import fr.hardel.leafs.chunk.pool.ChunkTask;
import fr.hardel.leafs.chunk.ticket.TicketGraphs;
import fr.hardel.leafs.chunk.ticket.TicketTimeoutIndex;
import fr.hardel.leafs.chunk.view.PlayerView;
import fr.hardel.leafs.metrics.MinuteCounter;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.RegionBorrow;
import fr.hardel.leafs.ticking.TickingManager;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.TicketStorage;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

public final class LevelChunks {
    private static final AtomicInteger IDS = new AtomicInteger();
    private final ChunkPool pool;
    private final ChunkPlacement placement;
    private final TicketGraphs graphs;
    private final TicketTimeoutIndex timeouts;
    private final PlayerView view;
    private final ChunkOwners owners;
    private final ChunkHolders holders;
    private final GenerationSteps steps;
    private final MinuteCounter chunksFull;
    private final UnownedSweep sweep;
    private final ChunkWrites writes;

    public LevelChunks(ChunkMap chunkMap, TicketStorage tickets, HolderTable table, PendingUnloads unloading, Executor serial) {
        ServerLevel level = chunkMap.level;
        LevelRegions regions = LevelRegions.of(level);
        TickingManager ticking = TickingManager.of(level.getServer());
        this.pool = ticking.chunkPool();
        TicketStorageAccess storage = (TicketStorageAccess) tickets;
        this.graphs = storage.leafs$graphs();
        this.timeouts = new TicketTimeoutIndex(tickets, chunkMap, graphs, regions.regionizer().sectionShift());
        storage.leafs$bindTimeouts(timeouts);
        ChunkOwners.Taker taker = (chunkX, chunkZ, task) -> take(regions, chunkX, chunkZ, task);
        this.placement = new ChunkPlacement(pool, IDS.getAndIncrement(), this::urgency);
        this.owners = new ChunkOwners(pool, placement, regions, level.getServer().getRunningThread(), serial, taker, ticking.globalScheduler());
        this.steps = new GenerationSteps(pool, placement);
        this.chunksFull = ticking.metrics().chunksFull();
        this.view = new PlayerView(tickets, graphs);
        this.holders = new ChunkHolders(chunkMap, graphs.loading(), table, unloading, owners, placement, tickets, steps, ticking.metrics());
        this.sweep = new UnownedSweep(level, regions, owners, pool, timeouts, table);
        this.writes = new ChunkWrites(pool, chunkMap.worker);
        ((ChunkWritesAccess) chunkMap.worker).leafs$bind(writes);
        graphs.listen(holders::publication, regions, view.tickets().and(placement.follow()), pool);
    }

    // Used by the Leafs Debug mod
    public static LevelChunks of(ServerLevel level) {
        return ((LevelChunksAccess) level.getChunkSource().chunkMap).leafs$chunks();
    }

    private static boolean take(LevelRegions regions, int chunkX, int chunkZ, Runnable task) {
        return RegionBorrow.hold(borrow -> {
            if (!borrow.tryBorrowChunk(regions, chunkX, chunkZ)) {
                return false;
            }

            task.run();
            return true;
        });
    }

    private int urgency(ChunkTask.Place place) {
        int chunkX = ChunkTask.chunkX(place.chunkKey());
        int chunkZ = ChunkTask.chunkZ(place.chunkKey());
        if (holders.demands().needs(chunkX, chunkZ, place.status())) {
            return ChunkPool.FIRST;
        }

        int chunk = view.urgency(chunkX, chunkZ);
        return place.chunkKey() == place.centerKey() ? chunk : Math.min(chunk, view.urgency(ChunkTask.chunkX(place.centerKey()), ChunkTask.chunkZ(place.centerKey())));
    }

    public ChunkPool pool() {
        return pool;
    }

    // Used by the Leafs Debug mod
    public TicketGraphs graphs() {
        return graphs;
    }

    public TicketTimeoutIndex timeouts() {
        return timeouts;
    }

    public PlayerView view() {
        return view;
    }

    public ChunkPlacement placement() {
        return placement;
    }

    public ChunkOwners owners() {
        return owners;
    }

    public ChunkHolders holders() {
        return holders;
    }

    public GenerationSteps steps() {
        return steps;
    }

    public Executor publisher(int chunkX, int chunkZ) {
        return task -> owners.publish(chunkX, chunkZ, () -> {
            task.run();
            chunksFull.increment();
        });
    }

    public UnownedSweep sweep() {
        return sweep;
    }

    public ChunkWrites writes() {
        return writes;
    }
}
