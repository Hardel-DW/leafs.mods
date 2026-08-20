package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.chunk.AreaPreload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.BlockUtil;
import net.minecraft.world.level.portal.PortalForcer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Optional;

// createPortal writes a frame after probing a 16-block spiral; its whole write square must be resident first, or a refusal mid-write would leave a partial frame.
@Mixin(PortalForcer.class)
public abstract class PortalForcerMixin {

    @Shadow
    @Final
    private ServerLevel level;

    @WrapMethod(method = "createPortal")
    private Optional<BlockUtil.FoundRectangle> leafs$demandWriteSquare(BlockPos origin, Direction.Axis axis, Operation<Optional<BlockUtil.FoundRectangle>> original) {
        AreaPreload.ensurePortalWriteSquare(level, origin);
        return original.call(origin, axis);
    }
}
