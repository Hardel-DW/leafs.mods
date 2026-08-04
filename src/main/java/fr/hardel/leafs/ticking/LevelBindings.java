package fr.hardel.leafs.ticking;

import fr.hardel.leafs.entity.EntityTeleports;
import fr.hardel.leafs.entity.ServerLevelEntityAccess;
import fr.hardel.leafs.global.GlobalServerAccess;
import fr.hardel.leafs.ownership.RegionContext;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.scheduler.RegionScheduler;
import fr.hardel.leafs.scheduler.SharedChunkHolds;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

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

    /** Vanilla ticket adds reached from region ticks defer to the level's single ticket mutator. */
    public static void addTicketWithRadius(ServerChunkCache chunkSource, TicketType type, ChunkPos pos, int radius) {
        ServerLevel level = chunkSource.level;
        LevelRegions regions = regionsOf(level);
        if (regions.ownership().isLevelSerialHeldByCurrentThread() || regions.body() == null) {
            chunkSource.addTicketWithRadius(type, pos, radius);

            return;
        }

        ((LeafsServerAccess) level.getServer()).leafs$ticking().submitToLevel(level, () -> chunkSource.addTicketWithRadius(type, pos, radius));
    }

    private static LevelRegions regionsOf(ServerLevel level) {
        return ((ServerLevelRegionAccess) level).leafs$regions();
    }
}
