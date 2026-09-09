package fr.hardel.leafs.mixin.light;

import ca.spottedleaf.starlight.common.light.StarLightInterface;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.pool.ChunkTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** ScalableLux hands its light updates to its own pool; they go to the chunk pool of the level, under the area they write. */
@Mixin(value = StarLightInterface.class, remap = false)
public abstract class StarLightInterfaceMixin {
    @Shadow
    public abstract Level getWorld();

    @WrapOperation(method = "schedulePropagation0", at = @At(value = "INVOKE", target = "Lca/spottedleaf/starlight/common/thread/SchedulingUtil;scheduleTask(ILjava/lang/Runnable;III)V"))
    private void leafs$onTheChunkPool(int owner, Runnable task, int chunkX, int chunkZ, int radius, Operation<Void> original) {
        LevelChunks.of((ServerLevel) getWorld()).owners().onPool(ChunkTask.Kind.LIGHT, chunkX, chunkZ, radius, task);
    }
}
