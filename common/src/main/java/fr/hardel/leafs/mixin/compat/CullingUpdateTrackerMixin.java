package fr.hardel.leafs.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.excess.ConcurrentLong2ObjectMap;
import fr.hardel.excess.ConcurrentLongSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = {
    // XFactHD/FramedBlocks, culling updates, enqueued by framed blocks from their region and walked and cleared per dimension by the level tick.
    "io.github.xfacthd.framedblocks.common.data.cullupdate.CullingUpdateTracker"
})
public abstract class CullingUpdateTrackerMixin {

    @WrapMethod(method = "lambda$enqueueCullingUpdate$0")
    private static Long2ObjectMap<LongSet> leafs$concurrentDimensionPositions(ResourceKey<Level> dimension, Operation<Long2ObjectMap<LongSet>> original) {
        return new ConcurrentLong2ObjectMap<>();
    }

    @WrapMethod(method = "lambda$enqueueCullingUpdate$1")
    private static LongSet leafs$concurrentChunkPositions(long chunk, Operation<LongSet> original) {
        return new ConcurrentLongSet();
    }
}
