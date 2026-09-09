package fr.hardel.leafs.chunk;

import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/** Vanilla's checkStart with the instance monitor on its two tables only: the disk scan and the placement check run outside it, and their answer enters where nothing fresher landed. */
public final class StructureChecks {

    private StructureChecks() {
    }

    public static StructureCheckResult check(StructureCheck check, ChunkPos pos, Structure structure, StructurePlacement placement, boolean requireUnreferenced) {
        long key = pos.pack();
        synchronized (check) {
            Object2IntMap<Structure> cached = check.loadedChunks.get(key);
            if (cached != null) {
                return check.checkStructureInfo(cached, structure, requireUnreferenced);
            }
        }

        StructureCheckResult stored = check.tryLoadFromStorage(pos, structure, requireUnreferenced, key);
        if (stored != null) {
            return stored;
        }

        if (!placement.applyAdditionalChunkRestrictions(pos.x(), pos.z(), check.seed)) {
            return StructureCheckResult.START_NOT_PRESENT;
        }

        return possible(check, key, pos, structure) ? StructureCheckResult.CHUNK_LOAD_NEEDED : StructureCheckResult.START_NOT_PRESENT;
    }

    /** The placement check of a chunk and structure runs off the monitor; the first answer stored wins, as vanilla computes it once. */
    private static boolean possible(StructureCheck check, long key, ChunkPos pos, Structure structure) {
        Long2BooleanMap known;
        synchronized (check) {
            known = check.featureChecks.computeIfAbsent(structure, _ -> new Long2BooleanOpenHashMap());
            if (known.containsKey(key)) {
                return known.get(key);
            }
        }

        boolean possible = check.canCreateStructure(pos, structure);
        synchronized (check) {
            known.putIfAbsent(key, possible);
            return known.get(key);
        }
    }
}
