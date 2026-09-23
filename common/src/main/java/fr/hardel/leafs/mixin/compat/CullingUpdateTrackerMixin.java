package fr.hardel.leafs.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.ConcurrentLong2ObjectMap;
import fr.hardel.excess.ConcurrentLongSet;
import fr.hardel.excess.SynchronizedReference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = {
    // XFactHD/FramedBlocks, culling updates, enqueued by framed blocks from their region and walked and cleared per dimension by the level tick.
    "io.github.xfacthd.framedblocks.common.data.cullupdate.CullingUpdateTracker"
})
public abstract class CullingUpdateTrackerMixin {

    @WrapOperation(method = "<clinit>", at = @At(value = "NEW", target = "it/unimi/dsi/fastutil/objects/Reference2ObjectOpenHashMap"))
    private static <K, V> Reference2ObjectOpenHashMap<K, V> leafs$synchronizedDimensions(Operation<Reference2ObjectOpenHashMap<K, V>> original) {
        return new SynchronizedReference2ObjectOpenHashMap<>();
    }

    @WrapMethod(method = "lambda$enqueueCullingUpdate$0")
    private static Long2ObjectMap<LongSet> leafs$concurrentDimensionPositions(ResourceKey<Level> dimension, Operation<Long2ObjectMap<LongSet>> original) {
        return new ConcurrentLong2ObjectMap<>();
    }

    @WrapMethod(method = "lambda$enqueueCullingUpdate$1")
    private static LongSet leafs$concurrentChunkPositions(long chunk, Operation<LongSet> original) {
        return new ConcurrentLongSet();
    }
}
