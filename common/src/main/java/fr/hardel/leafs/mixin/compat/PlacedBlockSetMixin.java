package fr.hardel.leafs.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.SynchronizedHashSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashSet;

@Pseudo
@Mixin(targets = {
    // stepsword/mahoutsukai, fog projectors, written by placing and removing from the block's region and walked and cleared by the level tick.
    "stepsword.mahoutsukai.block.FogProjector",
    // stepsword/mahoutsukai, murky water, written by flowing from the fluid's region and walked and cleared by the level tick.
    "stepsword.mahoutsukai.fluids.MurkyWaterBlock"
})
public abstract class PlacedBlockSetMixin {

    @WrapOperation(method = {"<clinit>", "onPlace", "affectNeighborsAfterRemoval"}, at = @At(value = "NEW", target = "java/util/HashSet"))
    private static <E> HashSet<E> leafs$synchronized(Operation<HashSet<E>> original) {
        return new SynchronizedHashSet<>();
    }
}
