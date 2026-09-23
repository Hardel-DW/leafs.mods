package fr.hardel.leafs.ticking;

import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.owner.ChunkClaim;
import fr.hardel.leafs.chunk.owner.ChunkOwners;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.region.RegionState;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;

public final class RegionBorrow {
    private static final ThreadLocal<RegionBorrow> CURRENT = new ThreadLocal<>();
    private static final long WAIT_NANOS = 50_000L;

    private final Set<Region<RegionTickData>> held = new LinkedHashSet<>();
    private final Map<LevelRegions, Long2ObjectOpenHashMap<ChunkClaim>> heldChunks = new LinkedHashMap<>();
    private final List<Runnable> releases = new ArrayList<>();

    private RegionBorrow() {
    }

    public static RegionBorrow enter() {
        RegionBorrow borrow = new RegionBorrow();
        CURRENT.set(borrow);
        return borrow;
    }

    public void exit() {
        releases.forEach(Runnable::run);
        CURRENT.remove();
    }

    public void keep(Runnable release) {
        releases.add(release);
    }

    public static <T> T hold(Function<RegionBorrow, T> body) {
        RegionBorrow current = CURRENT.get();
        if (current != null) {
            return body.apply(current);
        }

        RegionBorrow borrow = enter();
        try {
            return body.apply(borrow);
        } finally {
            borrow.releaseAll();
            borrow.exit();
        }
    }

    public static RegionBorrow current() {
        return CURRENT.get();
    }

    public static void atContact(Entity entity) {
        if (entity.level() instanceof ServerLevel level) {
            ChunkPos chunk = entity.chunkPosition();
            atContact(LevelRegions.of(level), chunk.x(), chunk.z());
        }
    }

    public static void atContact(LevelRegions regions, int chunkX, int chunkZ) {
        RegionBorrow borrow = CURRENT.get();
        if (borrow != null) {
            borrow.borrow(regions, chunkX, chunkZ);
        }
    }

    public static void lockAll(LevelRegions regions) {
        RegionBorrow borrow = CURRENT.get();
        if (borrow != null) {
            borrow.borrowAll(regions);
        }
    }

    public void borrow(LevelRegions regions, int chunkX, int chunkZ) {
        boolean serverThread = serverThread(regions);
        while (true) {
            Region<RegionTickData> region = regions.regionizer().regionAt(chunkX, chunkZ);
            if (region == null) {
                if (tryBorrowChunk(regions, chunkX, chunkZ) || !serverThread) {
                    return;
                }

                TickingManager.of(regions.level().getServer()).await(() -> tryBorrowChunk(regions, chunkX, chunkZ));
                return;
            }

            if (!serverThread || take(regions, region)) {
                return;
            }
        }
    }

    public void borrowAll(LevelRegions regions) {
        if (!serverThread(regions)) {
            return;
        }

        int before;
        do {
            before = held.size();
            for (Region<RegionTickData> region : regions.regionizer().regionsView()) {
                if (region.tryHold()) {
                    held.add(region);
                }
            }

            for (Region<RegionTickData> region : regions.regionizer().regionsView()) {
                take(regions, region);
            }
        } while (held.size() != before);
    }

    private static boolean serverThread(LevelRegions regions) {
        return !regions.live() || TickingManager.of(regions.level().getServer()).onServerThread();
    }

    private boolean take(LevelRegions regions, Region<RegionTickData> region) {
        while (region.state() != RegionState.DEAD) {
            if (held.contains(region) || region.tryHold()) {
                held.add(region);
                return true;
            }

            awaitTick(regions, region);
        }

        return false;
    }

    private void awaitTick(LevelRegions regions, Region<RegionTickData> region) {
        if (!regions.live()) {
            LockSupport.parkNanos(WAIT_NANOS);
            return;
        }

        ThreadWaits.Wait outer = ThreadWaits.open(() -> "waiting for %s in %s".formatted(region, regions.level().dimension().identifier()));
        try {
            TickingManager.of(regions.level().getServer()).await(() -> held.contains(region) || region.state() != RegionState.TICKING);
        } finally {
            ThreadWaits.close(outer);
        }
    }

    public boolean holds(LevelRegions regions, int chunkX, int chunkZ) {
        Long2ObjectOpenHashMap<ChunkClaim> chunks = heldChunks.get(regions);
        if (chunks != null && chunks.containsKey(ChunkPos.pack(chunkX, chunkZ))) {
            return true;
        }

        Region<RegionTickData> region = regions.regionizer().regionAt(chunkX, chunkZ);
        return region != null && held.contains(region);
    }

    public boolean tryBorrowChunk(LevelRegions regions, int chunkX, int chunkZ) {
        if (!regions.live()) {
            return true;
        }

        long key = ChunkPos.pack(chunkX, chunkZ);
        Long2ObjectOpenHashMap<ChunkClaim> chunks = heldChunks.computeIfAbsent(regions, _ -> new Long2ObjectOpenHashMap<>());
        if (chunks.containsKey(key)) {
            return true;
        }

        ChunkClaim taken = LevelChunks.of(regions.level()).owners().borrow(chunkX, chunkZ);
        if (taken == null) {
            return false;
        }

        chunks.put(key, taken);
        return true;
    }

    public void releaseAll() {
        for (Region<RegionTickData> region : held) {
            region.markNotTicking();
        }

        held.clear();
        heldChunks.forEach((regions, chunks) -> {
            ChunkOwners owners = LevelChunks.of(regions.level()).owners();
            for (Long2ObjectMap.Entry<ChunkClaim> entry : chunks.long2ObjectEntrySet()) {
                owners.release(ChunkPos.getX(entry.getLongKey()), ChunkPos.getZ(entry.getLongKey()), entry.getValue());
            }
        });
        
        heldChunks.clear();
    }

    public int drainInboxes() {
        int drained = 0;
        for (Region<RegionTickData> region : List.copyOf(held)) {
            drained += region.data().inbox().drainChunkWork();
        }

        for (Long2ObjectOpenHashMap<ChunkClaim> chunks : List.copyOf(heldChunks.values())) {
            for (ChunkClaim claim : List.copyOf(chunks.values())) {
                drained += claim.mail().drainChunkWork();
            }
        }

        return drained;
    }

    public int size() {
        return held.size();
    }
}
