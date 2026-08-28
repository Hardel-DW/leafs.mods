package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.RegionBorrow;
import fr.hardel.leafs.world.WorldTickContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.BlockUtil;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.portal.PortalForcer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Optional;

/** createPortal probes a 16-block spiral and writes a frame a few blocks wider: the writer takes every region of that square first, like a command would. */
@Mixin(PortalForcer.class)
public abstract class PortalForcerMixin {
    private static final int WRITE_CHUNK_RADIUS = 2;

    @Shadow
    @Final
    private ServerLevel level;

    @WrapMethod(method = "createPortal")
    private Optional<BlockUtil.FoundRectangle> leafs$borrowTheWriteSquare(BlockPos origin, Direction.Axis axis, Operation<Optional<BlockUtil.FoundRectangle>> original) {
        ChunkPos center = ChunkPos.containing(origin);
        LevelRegions regions = LevelRegions.of(level);
        WorldTickContext mine = WorldTickContext.current();
        return RegionBorrow.hold(mine == null ? null : mine.region(), borrow -> {
            for (int chunkX = center.x() - WRITE_CHUNK_RADIUS; chunkX <= center.x() + WRITE_CHUNK_RADIUS; chunkX++) {
                for (int chunkZ = center.z() - WRITE_CHUNK_RADIUS; chunkZ <= center.z() + WRITE_CHUNK_RADIUS; chunkZ++) {
                    borrow.borrow(regions, chunkX, chunkZ);
                }
            }

            return original.call(origin, axis);
        });
    }
}
