package fr.hardel.leafs.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.ConcurrentLong2ObjectMap;
import fr.hardel.excess.ConcurrentLongSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;
import java.util.function.Function;

@Pseudo
@Mixin(targets = {
    // XFactHD/FramedBlocks, culling updates, enqueued by framed blocks from their region and walked and cleared per dimension by the level tick.
    "io.github.xfacthd.framedblocks.common.data.cullupdate.CullingUpdateTracker"
})
public abstract class CullingUpdateTrackerMixin {

    @WrapOperation(method = "enqueueCullingUpdate",
        at = @At(value = "INVOKE", target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"))
    private static Object leafs$concurrentDimensionPositions(Map<?, ?> positions, Object dimension, Function<?, ?> mapping, Operation<Object> original) {
        return original.call(positions, dimension, (Function<ResourceKey<Level>, Long2ObjectMap<LongSet>>) _ -> new ConcurrentLong2ObjectMap<>());
    }

    @WrapOperation(method = "enqueueCullingUpdate",
        at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;computeIfAbsent(JLit/unimi/dsi/fastutil/longs/Long2ObjectFunction;)Ljava/lang/Object;"))
    private static Object leafs$concurrentChunkPositions(Long2ObjectMap<?> positions, long chunk, Long2ObjectFunction<?> mapping, Operation<Object> original) {
        return original.call(positions, chunk, (Long2ObjectFunction<LongSet>) _ -> new ConcurrentLongSet());
    }
}
