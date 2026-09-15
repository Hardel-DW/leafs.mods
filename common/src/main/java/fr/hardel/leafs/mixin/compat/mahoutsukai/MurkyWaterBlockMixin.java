package fr.hardel.leafs.mixin.compat.mahoutsukai;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.SynchronizedHashSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashSet;

/** stepsword/mahoutsukai, murky water. Flowing writes the set from the fluid's region; the level tick walks and clears it. */
@Pseudo
@Mixin(targets = "stepsword.mahoutsukai.fluids.MurkyWaterBlock")
public abstract class MurkyWaterBlockMixin {

    @WrapOperation(method = {"<clinit>", "onPlace", "affectNeighborsAfterRemoval"}, at = @At(value = "NEW", target = "java/util/HashSet"))
    private static <E> HashSet<E> leafs$sharedMurkies(Operation<HashSet<E>> original) {
        return new SynchronizedHashSet<>();
    }
}
