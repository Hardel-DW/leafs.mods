package fr.hardel.leafs.mixin.compat.laserio;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.SynchronizedArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;

/** Direwolf20-MC/LaserIO, particle lists. Cards fill the lists from their region; the server tick sends and clears them. */
@Pseudo
@Mixin(targets = "com.direwolf20.laserio.common.events.ServerTickHandler")
public abstract class ServerTickHandlerMixin {

    @WrapOperation(method = "<clinit>", at = @At(value = "NEW", target = "java/util/ArrayList"))
    private static <E> ArrayList<E> leafs$sharedParticleLists(Operation<ArrayList<E>> original) {
        return new SynchronizedArrayList<>();
    }
}
