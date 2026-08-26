package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.util.CrudeIncrementalIntIdentityHashBiMap;
import net.minecraft.world.level.chunk.HashMapPalette;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Vanilla adds the overflowing value before the resize, growing a palette a reader still snapshots. A full palette answers out of range instead, vanilla then resizes. */
@Mixin(HashMapPalette.class)
public abstract class HashMapPaletteMixin {

    @Shadow
    @Final
    private int bits;

    @WrapOperation(method = "idFor", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/CrudeIncrementalIntIdentityHashBiMap;add(Ljava/lang/Object;)I"))
    private int leafs$overflowWithoutTheAdd(CrudeIncrementalIntIdentityHashBiMap<Object> map, Object value, Operation<Integer> original) {
        if (map.size() >= 1 << this.bits) {
            return 1 << this.bits;
        }

        return original.call(map, value);
    }
}
