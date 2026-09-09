package fr.hardel.leafs.chunk;

import fr.hardel.leafs.chunk.disk.ChunkWrites;
import fr.hardel.leafs.chunk.holder.ChunkHolders;
import fr.hardel.leafs.chunk.holder.FullStep;
import fr.hardel.leafs.chunk.holder.GenerationSteps;
import fr.hardel.leafs.chunk.holder.HolderTable;
import fr.hardel.leafs.chunk.holder.PendingUnloads;
import fr.hardel.leafs.chunk.owner.ChunkOwners;
import fr.hardel.leafs.chunk.owner.UnownedSweep;
import fr.hardel.leafs.chunk.pool.ChunkPool;
import fr.hardel.leafs.chunk.ticket.TicketGraphs;
import fr.hardel.leafs.chunk.ticket.TicketTimeoutIndex;
import fr.hardel.leafs.chunk.view.PlayerView;
import fr.hardel.leafs.ticking.LevelRegions;

import fr.hardel.leafs.ticking.RegionBorrow;
import fr.hardel.leafs.ticking.TickingManager;
import fr.hardel.leafs.world.WorldTickContext;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.TicketStorage;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

public final class LevelChunks {
    private static final AtomicInteger IDS = new AtomicInteger();
    private final ChunkPool pool;
    private final TicketGraphs graphs;
    private final TicketTimeoutIndex timeouts;
    private final PlayerView view;
    private final ChunkOwners owners;
    private final ChunkHolders holders;
    private final GenerationSteps steps;
    private final FullStep full;
    private final UnownedSweep sweep;
    private final ChunkWrites writes;

    public LevelChunks(ChunkMap chunkMap, TicketStorage tickets, HolderTable table, PendingUnloads unloading, Executor serial) {
        ServerLevel level = chunkMap.level;
        LevelRegions regions = LevelRegions.of(level);
        TickingManager ticking = TickingManager.of(level.getServer());
        this.pool = ticking.chunkPool();
        TicketStorageAccess storage = (TicketStorageAccess) tickets;
        this.graphs = storage.leafs$graphs();
        this.timeouts = new TicketTimeoutIndex(tickets, graphs, regions.regionizer().sectionShift());
        storage.leafs$bindTimeouts(timeouts);
        ChunkOwners.Taker taker = (chunkX, chunkZ, task) -> take(regions, chunkX, chunkZ, task);
        this.owners = new ChunkOwners(pool, IDS.getAndIncrement(), regions::inboxAt, (chunkX, chunkZ) -> holds(level, regions, chunkX, chunkZ), this::urgency, regions::live, serial, taker, ticking.globalScheduler(), regions.slowTaskNanos());
        this.steps = new GenerationSteps(chunkMap, pool, owners, ticking.metrics());
        this.full = new FullStep(owners, ticking.metrics().chunksFull());
        this.view = new PlayerView(tickets, graphs);
        this.holders = new ChunkHolders(chunkMap, graphs.loading(), table, unloading, owners, tickets, steps, ticking.metrics());
        this.sweep = new UnownedSweep(level, regions, owners, pool, timeouts, table);
        this.writes = new ChunkWrites(pool, chunkMap.worker);
        ((ChunkWritesAccess) chunkMap.worker).leafs$bind(writes);
        graphs.listen(holders, regions, view.tickets().and(owners.follow()), pool);
        timeouts.pauseWhile(holders::busy);
    }

    public static LevelChunks of(ServerLevel level) {
        return ((LevelChunksAccess) level.getChunkSource().chunkMap).leafs$chunks();
    }

    private static boolean holds(ServerLevel level, LevelRegions regions, int chunkX, int chunkZ) {
        if (WorldTickContext.ownsChunk(level, chunkX, chunkZ)) {
            return true;
        }

        RegionBorrow borrow = RegionBorrow.current();
        if (borrow != null && borrow.holds(regions, chunkX, chunkZ)) {
            return true;
        }

        return !regions.live() && level.getServer().isSameThread();
    }

    /** Game work on a chunk no region covers: the calling thread takes the chunk for the task and reads back what it writes, like vanilla; false when another thread holds it. */
    private static boolean take(LevelRegions regions, int chunkX, int chunkZ, Runnable task) {
        return RegionBorrow.hold(borrow -> {
            if (!borrow.tryBorrowChunk(regions, chunkX, chunkZ)) {
                return false;
            }

            task.run();
            return true;
        });
    }

    /** The head of the pool while a thread waits for it, the distance to the nearest player otherwise. */
    private int urgency(int chunkX, int chunkZ) {
        return holders.demanded(chunkX, chunkZ) ? ChunkPool.FIRST : view.urgency(chunkX, chunkZ);
    }

    public ChunkPool pool() {
        return pool;
    }

    public TicketGraphs graphs() {
        return graphs;
    }

    public TicketTimeoutIndex timeouts() {
        return timeouts;
    }

    public PlayerView view() {
        return view;
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

    public FullStep full() {
        return full;
    }

    public UnownedSweep sweep() {
        return sweep;
    }

    public ChunkWrites writes() {
        return writes;
    }
}
