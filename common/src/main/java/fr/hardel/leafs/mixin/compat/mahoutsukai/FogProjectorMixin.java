package fr.hardel.leafs.mixin.compat.mahoutsukai;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.SynchronizedHashSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashSet;

/** stepsword/mahoutsukai, fog projectors. Placing and removing write the set from the block's region; the level tick walks and clears it. */
@Pseudo
@Mixin(targets = "stepsword.mahoutsukai.block.FogProjector")
public abstract class FogProjectorMixin {

    @WrapOperation(method = {"<clinit>", "onPlace", "affectNeighborsAfterRemoval"}, at = @At(value = "NEW", target = "java/util/HashSet"))
    private static <E> HashSet<E> leafs$sharedProjectors(Operation<HashSet<E>> original) {
        return new SynchronizedHashSet<>();
    }
}
