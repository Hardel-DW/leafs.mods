package fr.hardel.leafs.mixin.compat.productivebees;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.SynchronizedHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashMap;

/** JDKDigital/productivebees, recipe cache. Nest ticks fill the cache from their region. */
@Pseudo
@Mixin(targets = "cy.jdkdigital.productivebees.common.block.SolitaryNest")
public abstract class SolitaryNestMixin {

    @WrapOperation(method = "<clinit>", at = @At(value = "NEW", target = "java/util/HashMap"))
    private static <K, V> HashMap<K, V> leafs$sharedRecipes(Operation<HashMap<K, V>> original) {
        return new SynchronizedHashMap<>();
    }
}
