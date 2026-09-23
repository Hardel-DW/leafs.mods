package fr.hardel.leafs.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.SynchronizedLongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = {
    // JDKDigital/utilitarian, visited positions per dimension, filled by onLogBreak from the breaking player's region and cleared by the server tick.
    "cy.jdkdigital.utilitarian.event.ModEventHandler"
})
public abstract class LeafDecayMixin {

    @WrapOperation(method = "lambda$onLogBreak$0", at = @At(value = "NEW", target = "it/unimi/dsi/fastutil/longs/LongOpenHashSet"))
    private static LongOpenHashSet leafs$synchronized(Operation<LongOpenHashSet> original) {
        return new SynchronizedLongOpenHashSet();
    }
}
