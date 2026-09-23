package fr.hardel.leafs.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.SynchronizedArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;

@Pseudo
@Mixin(targets = {
    // Direwolf20-MC/LaserIO, particle lists, filled by cards from their region and sent and cleared by the server tick.
    "com.direwolf20.laserio.common.events.ServerTickHandler",
    // Direwolf20-MC/MiningGadgets, durability sync list, filled by mining from the player's region and sent and cleared by the server tick.
    "com.direwolf20.mininggadgets.common.events.ServerTickHandler",
    // JDKDigital/utilitarian, leaf decay queue, filled by onLogBreak from the breaking player's region and walked by the server tick.
    "cy.jdkdigital.utilitarian.event.ModEventHandler"
})
public abstract class StaticArrayListMixin {

    @WrapOperation(method = "<clinit>", at = @At(value = "NEW", target = "java/util/ArrayList"))
    private static <E> ArrayList<E> leafs$synchronized(Operation<ArrayList<E>> original) {
        return new SynchronizedArrayList<>();
    }
}
