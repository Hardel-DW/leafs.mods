package fr.hardel.leafs.ticking;

import fr.hardel.leafs.chunk.LeafsTicketTypes;
import fr.hardel.leafs.chunk.PropagatorAccess;
import fr.hardel.leafs.chunk.propagator.LevelTicketPropagator;
import fr.hardel.leafs.entity.EntityTeleports;
import fr.hardel.leafs.entity.ServerLevelEntityAccess;
import fr.hardel.leafs.global.GlobalServerAccess;
import fr.hardel.leafs.ownership.RegionContext;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.scheduler.RegionScheduler;
import fr.hardel.leafs.scheduler.SharedChunkHolds;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Wiring factories: they hand ticking/'s per-level surfaces to modules that must not import ticking/. */
public final class LevelBindings {

    private LevelBindings() {
    }

    public static EntityTeleports entityTeleports(ServerLevel level) {
        LeafsServerAccess server = (LeafsServerAccess) level.getServer();

        return new EntityTeleports(level, new EntityTeleports.LevelBinding() {

            @Override
            public SharedChunkHolds holds() {
                return regionsOf(level).holds();
            }

            @Override
            public boolean currentRegionOwns(int chunkX, int chunkZ) {
                if (!(RegionContext.current() instanceof RegionContext.Region context)) {
                    return false;
                }

                Region<RegionTickData> owner = regionsOf(level).regionizer().regionAtUnsynchronised(chunkX, chunkZ);

                return owner != null && owner.id() == context.id();
            }

            @Override
            public void submitSerial(Runnable task) {
                server.leafs$ticking().submitToLevel(level, task);
            }

            @Override
            public void submitWindow(Runnable task) {
                ((GlobalServerAccess) level.getServer()).leafs$barrierWindow().enqueue(task);
            }

            @Override
            public void submitPlacement(int chunkX, int chunkZ, Runnable placement) {
                RegionScheduler<RegionTickData> scheduler = regionsOf(level).taskScheduler();
                if (scheduler != null) {
                    scheduler.queue(chunkX, chunkZ, placement);
                } else {
                    submitSerial(placement);
                }
            }
        }, target -> ((ServerLevelEntityAccess) target).leafs$entityTeleports());
    }

    /**
     * A refused chunk read files a short-lived ticket, drains it and requests the status, so the
     * chunk actually loads and the vanilla retry finds it. The ticket alone would only pin a holder:
     * a level like 41 for STRUCTURE_STARTS triggers no promotion, which is what left structure
     * spawn positions unloadable forever (roadmap 11).
     */
    public static Runnable chunkDemand(ServerChunkCache chunkSource, int chunkX, int chunkZ, ChunkStatus status) {
        return () -> {
            chunkSource.ticketStorage.addTicket(new Ticket(LeafsTicketTypes.demand, ChunkLevel.byStatus(status)), new ChunkPos(chunkX, chunkZ));
            LevelTicketPropagator propagator = ((PropagatorAccess) chunkSource.chunkMap.getDistanceManager()).leafs$propagator();
            propagator.drain();
            ChunkHolder holder = chunkSource.chunkMap.getUpdatingChunkIfPresent(ChunkPos.pack(chunkX, chunkZ));
            if (holder != null) {
                propagator.scheduling().requestArea(chunkX, chunkZ, 0, () -> holder.scheduleChunkGenerationTask(status, chunkSource.chunkMap));
            }
        };
    }

    private static LevelRegions regionsOf(ServerLevel level) {
        return ((ServerLevelRegionAccess) level).leafs$regions();
    }
}
