package fr.hardel.leafs.world;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/** Vanilla's sendBlockUpdated against the state of the region ticking on this thread; any other thread keeps the level's, never another region's. */
public final class LevelBlockUpdates {

    private LevelBlockUpdates() {
    }

    public static void onBlockUpdated(ServerLevel level, BlockPos pos, BlockState old, BlockState current) {
        level.getChunkSource().blockChanged(pos);
        RegionWorldData ticking = WorldTickContext.activeFor(level);
        if (ticking == null) {
            level.getPathTypeCache().invalidate(pos);
            return;
        }

        ticking.pathTypeCache().invalidate(pos);
        VoxelShape oldShape = old.getCollisionShape(level, pos);
        VoxelShape newShape = current.getCollisionShape(level, pos);
        if (!Shapes.joinIsNotEmpty(oldShape, newShape, BooleanOp.NOT_SAME)) {
            return;
        }

        List<PathNavigation> navigationsToUpdate = new ObjectArrayList<>();
        ticking.entities().forEachMob(mob -> {
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
