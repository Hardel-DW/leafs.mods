package fr.hardel.leafs.chunk.pool;

import fr.hardel.leafs.chunk.level.LevelListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public final class ChunkPlacement {
    private final ChunkPool pool;
    private final int level;
    private final Urgency urgency;

    public ChunkPlacement(ChunkPool pool, int level, Urgency urgency) {
        this.pool = pool;
        this.level = level;
        this.urgency = urgency;
    }

    public void onPool(ChunkTask.Kind kind, ChunkStatus status, int chunkX, int chunkZ, int radius, Runnable task) {
        pool.submit(ChunkTask.of(kind, place(chunkX, chunkZ, chunkX, chunkZ, status), area(kind, chunkX, chunkZ, radius), task));
    }

    public ChunkTask.Place place(int chunkX, int chunkZ, int centerX, int centerZ, ChunkStatus status) {
        return new ChunkTask.Place(ChunkTask.key(level, chunkX, chunkZ), ChunkTask.key(level, centerX, centerZ), status, urgency);
    }

    public long[] area(ChunkTask.Kind kind, int chunkX, int chunkZ, int radius) {
        if (radius < 0) {
            return ChunkTask.NO_RESERVATION;
        }

        int space = kind == ChunkTask.Kind.LIGHT ? level * 2 + 1 : level * 2;
        int side = 2 * radius + 1;
        long[] keys = new long[side * side];
        int count = 0;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                keys[count++] = ChunkTask.key(space, chunkX + dx, chunkZ + dz);
            }
        }

        return keys;
    }

    public LevelListener follow() {
        return (chunkKey, _, _) -> pool.changed(ChunkTask.key(level, ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey)));
    }

    public void expedite(ChunkNeed need) {
        int radius = need.radius();
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                pool.expedite(ChunkTask.key(level, need.chunkX() + dx, need.chunkZ() + dz), place -> serves(need, place));
            }
        }
    }

    public boolean help(ChunkNeed need, boolean holdsTheChunk) {
        return pool.help(task -> switch (task.kind()) {
            case STEP, LIGHT -> serves(need, task.place());
            case OWNER -> holdsTheChunk && serves(need, task.place());
            case HOUSEKEEPING -> false;
        });
    }

    private boolean serves(ChunkNeed need, ChunkTask.Place place) {
        long key = place.chunkKey();
        return ChunkTask.owner(key) == level && need.covers(ChunkTask.chunkX(key), ChunkTask.chunkZ(key), place.status());
    }

    public int queuedAt(int chunkX, int chunkZ) {
        return pool.queuedAt(ChunkTask.key(level, chunkX, chunkZ));
    }
}
