package fr.hardel.leafs.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.SynchronizedLongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;
import java.util.function.Function;

@Pseudo
@Mixin(targets = {
    // JDKDigital/utilitarian, visited positions per dimension, filled by onLogBreak from the breaking player's region and cleared by the server tick.
    "cy.jdkdigital.utilitarian.event.ModEventHandler"
})
public abstract class LeafDecayMixin {

    @WrapOperation(method = "onLogBreak", at = @At(value = "INVOKE", target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"))
    private static Object leafs$synchronized(Map<?, ?> visited, Object dimension, Function<?, ?> mapping, Operation<Object> original) {
        return original.call(visited, dimension, (Function<ResourceKey<Level>, LongOpenHashSet>) _ -> new SynchronizedLongOpenHashSet());
    }
}
