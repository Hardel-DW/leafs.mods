package fr.hardel.leafs.world;

import fr.hardel.leafs.entity.LevelEntityLists;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * Vanilla {@code ServerLevel.sendBlockUpdated} with the two level-wide structures resolved to the
 * position's owner: the path-type cache and the navigating-mob set. Only the owning unit's mobs
 * repath (drop-not-crash); vanilla's {@code isUpdatingNavigations} re-entry warning is dropped, the
 * collected snapshot makes mutation during recompute safe by construction.
 */
public final class LevelBlockUpdates {

    private LevelBlockUpdates() {
    }

    public static void onBlockUpdated(ServerLevel level, WorldDataRouter router, LevelEntityLists lists, BlockPos pos, BlockState old, BlockState current) {
        level.getChunkSource().blockChanged(pos);
        router.at(pos).pathTypeCache().invalidate(pos);
        VoxelShape oldShape = old.getCollisionShape(level, pos);
        VoxelShape newShape = current.getCollisionShape(level, pos);
        if (!Shapes.joinIsNotEmpty(oldShape, newShape, BooleanOp.NOT_SAME)) {
            return;
        }

        List<PathNavigation> navigationsToUpdate = new ObjectArrayList<>();
        lists.forEachNavigatingMobAt(ChunkPos.pack(pos), mob -> {
            PathNavigation navigation = mob.getNavigation();
            if (navigation.shouldRecomputePath(pos)) {
                navigationsToUpdate.add(navigation);
            }
        });
        for (PathNavigation navigation : navigationsToUpdate) {
            navigation.recomputePath();
        }
    }
}
