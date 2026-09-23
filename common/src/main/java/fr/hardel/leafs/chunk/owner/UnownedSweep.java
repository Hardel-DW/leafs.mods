package fr.hardel.leafs.chunk.owner;

import fr.hardel.leafs.chunk.ChangedChunksAccess;
import fr.hardel.leafs.chunk.ChunkBroadcasts;
import fr.hardel.leafs.chunk.holder.HolderTable;
import fr.hardel.leafs.chunk.pool.ChunkPool;
import fr.hardel.leafs.chunk.ticket.TicketTimeoutIndex;
import fr.hardel.leafs.entity.RegionEntityPersistence;
import fr.hardel.leafs.entity.ServerLevelEntityAccess;
import fr.hardel.leafs.region.Regionizer;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.RegionTickData;
import fr.hardel.leafs.world.ChunkSaves;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UnownedSweep {
    private final ServerLevel level;
    private final LevelRegions regions;
    private final ChunkOwners owners;
    private final ChunkPool pool;
    private final TicketTimeoutIndex timeouts;
    private final HolderTable table;
    private final ChunkSaves saves;
    private final AtomicBoolean sweeping = new AtomicBoolean();
    private final LongArrayList epochBacklog = new LongArrayList();
    private long epochSeen;

    public UnownedSweep(ServerLevel level, LevelRegions regions, ChunkOwners owners, ChunkPool pool, TicketTimeoutIndex timeouts, HolderTable table) {
        this.level = level;
        this.regions = regions;
        this.owners = owners;
        this.pool = pool;
        this.timeouts = timeouts;
        this.table = table;
        this.saves = new ChunkSaves(level);
    }

    public void soon() {
        if (sweeping.compareAndSet(false, true)) {
            pool.execute(this::sweep);
        }
    }

    private void sweep() {
        try {
            purgeTimeouts();
            unloadHiddenEntities();
            saveEagerly();
            saveBehindEpoch();
            broadcast();
        } finally {
            sweeping.set(false);
        }
    }

    private boolean unowned(long chunkKey) {
        return regions.regionizer().regionAt(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey)) == null;
    }

    private void dispatch(long chunkKey, Runnable task) {
        owners.submit(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey), Work.CHUNK, task);
    }

    private void purgeTimeouts() {
        Regionizer<RegionTickData> regionizer = regions.regionizer();
        int shift = regionizer.sectionShift();
        timeouts.purgeUnowned(section -> regionizer.regionAt(ChunkPos.getX(section) << shift, ChunkPos.getZ(section) << shift) != null);
    }

    private void unloadHiddenEntities() {
        RegionEntityPersistence persistence = ((ServerLevelEntityAccess) level).leafs$entityPersistence();
        int attempts = 0;
        for (long chunkKey : persistence.pendingUnloads().toLongArray()) {
            if (attempts == ChunkSaves.CHUNKS_PER_TICK) {
                return;
            }

            if (unowned(chunkKey)) {
                dispatch(chunkKey, () -> persistence.unloadHidden(chunkKey));
                attempts++;
            }
        }
    }

    private void saveEagerly() {
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        int attempts = 0;
        for (long chunkKey : chunkMap.chunksToEagerlySave.toLongArray()) {
            if (attempts == ChunkSaves.CHUNKS_PER_TICK) {
                return;
            }

            ChunkHolder holder = table.get(chunkKey);
            if (holder == null) {
                chunkMap.chunksToEagerlySave.remove(chunkKey);
            } else if (unowned(chunkKey)) {
                dispatch(chunkKey, () -> saves.saveEagerly(holder));
                attempts++;
            }
        }
    }

    private void saveBehindEpoch() {
        long epoch = regions.autosaveEpoch();
        if (epoch != epochSeen) {
            epochSeen = epoch;
            epochBacklog.clear();
            for (ChunkHolder holder : table.values()) {
                long chunkKey = holder.getPos().pack();
                if (unowned(chunkKey)) {
                    epochBacklog.add(chunkKey);
                }
            }
        }

        int budget = Math.min(ChunkSaves.CHUNKS_PER_TICK, epochBacklog.size());
        for (int index = 0; index < budget; index++) {
            long chunkKey = epochBacklog.popLong();
            ChunkHolder holder = table.get(chunkKey);
            if (holder != null) {
                dispatch(chunkKey, () -> saves.saveBehindEpoch(holder, epoch));
            }
        }
    }

    private void broadcast() {
        Set<ChunkHolder> changed = ((ChangedChunksAccess) level.getChunkSource()).leafs$changedHolders();
        for (ChunkHolder holder : changed) {
            long chunkKey = holder.getPos().pack();
            if (unowned(chunkKey) && changed.remove(holder)) {
                dispatch(chunkKey, () -> ChunkBroadcasts.changed(changed, List.of(holder)));
            }
        }
    }
}
