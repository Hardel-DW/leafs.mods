package fr.hardel.leafs.chunk;

import fr.hardel.leafs.chunk.propagator.LevelTicketPropagator;
import fr.hardel.leafs.chunk.propagator.SimulationLevels;
import fr.hardel.leafs.entity.RegionEntityPersistence;
import fr.hardel.leafs.entity.ServerLevelEntityAccess;
import fr.hardel.leafs.region.CoordinateKey;
import fr.hardel.leafs.region.Regionizer;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.RegionTickData;
import fr.hardel.leafs.world.ChunkSaves;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterable;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** The loaded chunks no region owns, the view beyond every ring: the chunk workers keep their mail, saves, unloads and broadcasts. One sweep per level tick, work only. */
public final class WorkerOwnedChunks {
    private final ServerLevel level;
    private final LevelRegions regions;
    private final ChunkMailbox mailbox;
    private final ChunkUnloads unloads;
    private final Executor workers;
    private final ChunkSaves saves;
    private final AtomicBoolean sweeping = new AtomicBoolean();
    private final LongArrayList epochBacklog = new LongArrayList();
    private long epochSeen;

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

    /** The chunks with work are claimed for the whole pass: a region adopting one meanwhile waits at its drain for the release. */
    private void sweep() {
        try {
            purgeTimedOutTickets();
            LongOpenHashSet claimed = claimWithWork();
            try {
                for (long key : claimed) {
                    mailbox.drain(key);
                }

                persistence().unloadHidden(claimed::contains);
                unloads.decide(claimed::contains);
                List<ChunkHolder> holders = holders(claimed);
                saves.saveEagerly(holders);
                saveBehindEpoch(holders);
                broadcast(holders);
            } finally {
                for (long key : claimed) {
                    mailbox.releaseToWorkers(key);
                }
            }
        } finally {
            sweeping.set(false);
        }
    }

    /** Mail, entity unloads, drops, eager saves, changes to broadcast and the epoch backlog name the chunks; the rest is nobody's work this pass. */
    private LongOpenHashSet claimWithWork() {
        LongOpenHashSet claimed = new LongOpenHashSet();
        claim(claimed, LongArrayList.wrap(mailbox.keys()));
        claim(claimed, persistence().pendingUnloads());
        claim(claimed, unloads.pending());
        claim(claimed, level.getChunkSource().chunkMap.chunksToEagerlySave);
        for (ChunkHolder holder : changedHolders()) {
            claim(claimed, holder.getPos().pack());
        }

        claim(claimed, epochBacklog());
        return claimed;
    }

    private void claim(LongOpenHashSet claimed, LongIterable keys) {
        for (long key : keys) {
            claim(claimed, key);
        }
    }

    private void claim(LongOpenHashSet claimed, long key) {
        if (!claimed.contains(key) && owns(key) && mailbox.tryClaim(key)) {
            claimed.add(key);
        }
    }

    /** A new epoch lists every owned holder once; each sweep takes its budget off the list. */
    private LongArrayList epochBacklog() {
        long epoch = regions.autosaveEpoch();
        if (epoch != epochSeen) {
            epochSeen = epoch;
            epochBacklog.clear();
            for (ChunkHolder holder : level.getChunkSource().chunkMap.visibleChunkMap.values()) {
                if (owns(holder.getPos().pack())) {
                    epochBacklog.add(holder.getPos().pack());
                }
            }
        }

        int budget = regions.autosaveForced() ? epochBacklog.size() : Math.min(ChunkSaves.CHUNKS_PER_TICK, epochBacklog.size());
        LongArrayList batch = new LongArrayList(epochBacklog.subList(epochBacklog.size() - budget, epochBacklog.size()));
        epochBacklog.removeElements(epochBacklog.size() - budget, epochBacklog.size());
        return batch;
    }

    private List<ChunkHolder> holders(LongOpenHashSet claimed) {
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        List<ChunkHolder> holders = new ArrayList<>(claimed.size());
        for (long key : claimed) {
            ChunkHolder holder = chunkMap.getVisibleChunkIfPresent(key);
            if (holder != null) {
                holders.add(holder);
            }
        }

        return holders;
    }

    private void saveBehindEpoch(List<ChunkHolder> holders) {
        long epoch = regions.autosaveEpoch();
        for (ChunkHolder holder : holders) {
            saves.saveBehindEpoch(holder, epoch);
        }
    }

    private void broadcast(List<ChunkHolder> holders) {
        ChunkBroadcasts.changed(holders);
        changedHolders().removeAll(holders);
    }

    private Set<ChunkHolder> changedHolders() {
        return ((ChangedChunksAccess) level.getChunkSource()).leafs$changedHolders();
    }

    private RegionEntityPersistence persistence() {
        return ((ServerLevelEntityAccess) level).leafs$entityPersistence();
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
