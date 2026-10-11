package fr.hardel.leafs.gametest;

import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.RegionChunkAccess;
import fr.hardel.leafs.chunk.holder.HolderTable;
import fr.hardel.leafs.chunk.owner.ChunkOwners;
import fr.hardel.leafs.chunk.owner.Work;
import fr.hardel.leafs.region.CoordinateKey;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.RegionTickData;
import fr.hardel.leafs.world.RegionChunks;
import fr.hardel.leafs.world.WorldTickContext;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public final class RegionChunksTest {
    private static final int FAR = 2000;
    private static final int BESIDE = 4;
    private static final int AWAY = 300;
    private static final int AROUND = 1;

    /** A region keeps its chunks section by section: its lists must stay what a full read of the table gives, through arrivals, a merge and dying sections. */
    @GameTest(maxTicks = 200000)
    public void theChunksOfARegionAreTheOnesOfItsTable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 spawn = Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO));
        ChunkPos far = PlayerTickTest.rawChunk(helper, FAR);
        ServerPlayer first = PlayerTickTest.joined(helper, spawn);
        ServerPlayer second = PlayerTickTest.joined(helper, spawn);
        Audit audit = new Audit(level);

        helper.startSequence()
            .thenExecute(() -> {
                arrive(first);
                arrive(second);
                land(first, 2, far);
            })
            .thenWaitUntil(() -> audit.settled(helper, far))
            .thenExecute(() -> land(second, 2, new ChunkPos(far.x() + BESIDE, far.z())))
            .thenWaitUntil(() -> audit.settled(helper, new ChunkPos(far.x() + BESIDE, far.z())))
            .thenExecute(() -> land(second, 3, new ChunkPos(far.x() + AWAY, far.z())))
            .thenWaitUntil(() -> audit.settled(helper, new ChunkPos(far.x() + AWAY, far.z())))
            .thenExecute(() -> {
                level.getServer().getPlayerList().remove(first);
                level.getServer().getPlayerList().remove(second);
            })
            .thenSucceed();
    }

    private static void arrive(ServerPlayer player) {
        player.connection.tick();
        PlayerTickTest.accept(player, 1, player.position());
    }

    private static void land(ServerPlayer player, int teleport, ChunkPos chunk) {
        Vec3 position = PlayerTickTest.onRawTerrain(chunk);
        player.connection.teleport(position.x, position.y, position.z, 0, 0);
        player.connection.resetPosition();
        PlayerTickTest.accept(player, teleport, position);
    }

    /** Reads the table of every region on its own thread and compares it with the lists the region keeps. */
    private static final class Audit {
        private final ServerLevel level;
        private final Queue<String> differences = new ConcurrentLinkedQueue<>();
        private final AtomicInteger pending = new AtomicInteger();
        private final AtomicInteger simulated = new AtomicInteger();
        private boolean asked;
        private String last = "nothing yet";

        private Audit(ServerLevel level) {
            this.level = level;
        }

        private void settled(GameTestHelper helper, ChunkPos simulatedAround) {
            if (!asked) {
                ask();
            }

            helper.assertTrue(pending.get() == 0, "the regions are being read, the last read found: %s".formatted(last));
            asked = false;
            last = differences.isEmpty() ? "%s simulated chunks, and the ones around %s do not all tick yet".formatted(simulated.get(), simulatedAround) : String.join("; ", differences);
            helper.assertTrue(differences.isEmpty() && ticksAround(simulatedAround), last);
        }

        private boolean ticksAround(ChunkPos center) {
            for (int dx = -AROUND; dx <= AROUND; dx++) {
                for (int dz = -AROUND; dz <= AROUND; dz++) {
                    if (!level.getChunkSource().isPositionTicking(ChunkPos.pack(center.x() + dx, center.z() + dz))) {
                        return false;
                    }
                }
            }

            return true;
        }

        private void ask() {
            asked = true;
            differences.clear();
            simulated.set(0);
            LevelRegions regions = LevelRegions.of(level);
            ChunkOwners owners = LevelChunks.of(level).owners();
            int shift = regions.regionizer().sectionShift();
            for (Region<RegionTickData> region : regions.regionizer().regionsView()) {
                long[] sections = region.sectionKeySnapshot();
                if (sections.length == 0) {
                    continue;
                }

                pending.incrementAndGet();
                owners.later(CoordinateKey.x(sections[0]) << shift, CoordinateKey.z(sections[0]) << shift, Work.GAME, () -> {
                    try {
                        read(region);
                    } finally {
                        pending.decrementAndGet();
                    }
                });
            }
        }

        private void read(Region<RegionTickData> region) {
            WorldTickContext tick = WorldTickContext.current();
            if (tick == null || tick.region() != region) {
                return;
            }

            LevelChunks chunks = LevelChunks.of(level);
            HolderTable table = chunks.holders().table();
            ChunkOwners owners = chunks.owners();
            Set<ChunkHolder> holders = Collections.newSetFromMap(new IdentityHashMap<>());
            Set<LevelChunk> ticking = Collections.newSetFromMap(new IdentityHashMap<>());
            Set<LevelChunk> simulates = Collections.newSetFromMap(new IdentityHashMap<>());
            Set<EntitySection<Entity>> entities = Collections.newSetFromMap(new IdentityHashMap<>());
            EntitySectionStorage<Entity> storage = level.entityManager.sectionStorage;
            for (long section : region.sectionKeySnapshot()) {
                table.forEachHolderIn(section, holder -> {
                    ChunkPos pos = holder.getPos();
                    if (owners.heldElsewhere(pos.x(), pos.z()) || RegionChunkAccess.fullChunkOrNull(holder) == null) {
                        return;
                    }

                    holders.add(holder);
                    for (long key : storage.getChunkSections(pos.x(), pos.z())) {
                        EntitySection<Entity> entitySection = storage.sections.get(key);
                        if (entitySection != null) {
                            entities.add(entitySection);
                        }
                    }

                    LevelChunk chunk = ChunkLevel.isBlockTicking(holder.getTicketLevel()) ? holder.getTickingChunk() : null;
                    if (chunk == null) {
                        return;
                    }

                    ticking.add(chunk);
                    if (level.shouldTickBlocksAt(pos.pack())) {
                        simulates.add(chunk);
                    }
                });
            }

            RegionChunks kept = region.data().worldData().chunks();
            compare(region, "holders", holders, kept.holders());
            compare(region, "ticking chunks", ticking, kept.ticking());
            compare(region, "simulated chunks", simulates, kept.simulated());
            compare(region, "entity sections", entities, kept.entitySections());
            simulated.addAndGet(kept.simulated().size());
        }

        private <T> void compare(Region<RegionTickData> region, String what, Set<T> table, Collection<T> kept) {
            long missing = table.stream().filter(element -> !kept.contains(element)).count();
            long stale = kept.stream().filter(element -> !table.contains(element)).count();
            if (missing + stale > 0) {
                differences.add("region #%s keeps %s %s, its table has %s: %s missing, %s stale".formatted(region.id(), kept.size(), what, table.size(), missing, stale));
            }
        }
    }
}
