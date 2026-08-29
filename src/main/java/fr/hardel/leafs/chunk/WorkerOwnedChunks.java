package fr.hardel.leafs.chunk;

import fr.hardel.leafs.chunk.propagator.LevelTicketPropagator;
import fr.hardel.leafs.chunk.propagator.SimulationLevels;
import fr.hardel.leafs.entity.ServerLevelEntityAccess;
import fr.hardel.leafs.region.CoordinateKey;
import fr.hardel.leafs.region.Regionizer;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.RegionTickData;
import fr.hardel.leafs.world.ChunkSaves;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** The loaded chunks no region owns, the view distance beyond every ring: the chunk workers keep their mail, their saves and their unloads. One sweep at a time per level, asked for once per level tick, never run by the server thread. */
public final class WorkerOwnedChunks {
    private final ServerLevel level;
    private final LevelRegions regions;
    private final ChunkMailbox mailbox;
    private final ChunkUnloads unloads;
    private final Executor workers;
    private final ChunkSaves saves;
    private final AtomicBoolean sweeping = new AtomicBoolean();

    public WorkerOwnedChunks(ServerLevel level, LevelRegions regions, ChunkMailbox mailbox, ChunkUnloads unloads, Executor workers) {
        this.level = level;
        this.regions = regions;
        this.mailbox = mailbox;
        this.unloads = unloads;
        this.workers = workers;
        this.saves = new ChunkSaves(level);
    }

    public boolean owns(long chunkKey) {
        return regions.regionizer().regionAt(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey)) == null;
    }

    /** One sweep in flight at most; a second ask while one runs is the next tick's. */
    public void sweepSoon() {
        if (sweeping.compareAndSet(false, true)) {
            workers.execute(this::sweep);
        }
    }

    /** The chunks claimed for the whole pass: a region adopting one meanwhile waits at its drain for the release. */
    private void sweep() {
        try {
            LongOpenHashSet claimed = claimOwned();
            try {
                purgeTimedOutTickets();
                for (long key : claimed) {
                    mailbox.drain(key);
                }

                ((ServerLevelEntityAccess) level).leafs$entityPersistence().unloadHidden(claimed::contains);
                unloads.decide(claimed::contains);
                saves.saveEagerly(claimed::contains);
                saveBehindEpoch(claimed);
            } finally {
                for (long key : claimed) {
                    mailbox.releaseToWorkers(key);
                }
            }
        } finally {
            sweeping.set(false);
        }
    }

    private LongOpenHashSet claimOwned() {
        LongOpenHashSet claimed = new LongOpenHashSet();
        for (ChunkHolder holder : level.getChunkSource().chunkMap.visibleChunkMap.values()) {
            long key = holder.getPos().pack();
            if (owns(key) && mailbox.tryClaim(key)) {
                claimed.add(key);
            }
        }

        return claimed;
    }

    private void saveBehindEpoch(LongOpenHashSet claimed) {
        long epoch = regions.autosaveEpoch();
        int budget = regions.autosaveForced() ? Integer.MAX_VALUE : ChunkSaves.CHUNKS_PER_TICK;
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        for (long key : claimed) {
            ChunkHolder holder = chunkMap.getVisibleChunkIfPresent(key);
            if (holder != null && saves.saveBehindEpoch(holder, epoch) && --budget == 0) {
                return;
            }
        }
    }

    /** The timeout tickets of the sections no region owns count down here; an expiry retires a holder level, so both authorities drain right after. */
    private void purgeTimedOutTickets() {
        TicketTimeoutIndex timeouts = ((TicketStorageAccess) level.getChunkSource().ticketStorage).leafs$timeouts();
        if (timeouts == null || timeouts.isEmpty()) {
            return;
        }

        Regionizer<RegionTickData> regionizer = regions.regionizer();
        int chunkShift = regionizer.sectionShift();
        if (timeouts.purgeUnowned(section -> regionizer.regionAt(CoordinateKey.x(section) << chunkShift, CoordinateKey.z(section) << chunkShift) != null) > 0) {
            PropagatorAccess access = (PropagatorAccess) level.getChunkSource().chunkMap.getDistanceManager();
            SimulationLevels simulation = access.leafs$simulation();
            LevelTicketPropagator propagator = access.leafs$propagator();
            simulation.drain();
            propagator.drain();
        }
    }
}
