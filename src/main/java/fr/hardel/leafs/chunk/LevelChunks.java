package fr.hardel.leafs.chunk;

import fr.hardel.leafs.chunk.holder.ChunkHolders;
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

/** The chunk system of one level, built with its ChunkMap. */
public final class LevelChunks {
    private static final AtomicInteger IDS = new AtomicInteger();

    private final TicketGraphs graphs;
    private final TicketTimeoutIndex timeouts;
    private final PlayerView view;
    private final ChunkOwners owners;
    private final ChunkHolders holders;
    private final GenerationSteps steps;
    private final UnownedSweep sweep;

    public LevelChunks(ChunkMap chunkMap, TicketStorage tickets, HolderTable table, PendingUnloads unloading, Executor serial) {
        ServerLevel level = chunkMap.level;
        LevelRegions regions = LevelRegions.of(level);
        TickingManager ticking = TickingManager.of(level.getServer());
        ChunkPool pool = ticking.chunkPool();
        TicketStorageAccess storage = (TicketStorageAccess) tickets;
        this.graphs = storage.leafs$graphs();
        this.timeouts = new TicketTimeoutIndex(tickets, regions.regionizer().sectionShift());
        storage.leafs$bindTimeouts(timeouts);
        this.view = new PlayerView(tickets, graphs);
        this.owners = new ChunkOwners(pool, IDS.getAndIncrement(), regions::inboxAt, (chunkX, chunkZ) -> holds(level, regions, chunkX, chunkZ), view::level, regions::live, serial);
        this.holders = new ChunkHolders(chunkMap, graphs.loading(), table, unloading, owners, tickets, ticking.metrics());
        this.steps = new GenerationSteps(pool, owners);
        this.sweep = new UnownedSweep(level, regions, owners, pool, timeouts, table);
        graphs.listen(holders, regions);
        timeouts.pauseWhile(holders::busy);
    }

    public static LevelChunks of(ServerLevel level) {
        return ((LevelChunksAccess) level.getChunkSource().chunkMap).leafs$chunks();
    }

    /** The current thread's right to write at a chunk: its region ticking, a borrow holding it, or the server thread while the regions are not running. */
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

    public UnownedSweep sweep() {
        return sweep;
    }
}
