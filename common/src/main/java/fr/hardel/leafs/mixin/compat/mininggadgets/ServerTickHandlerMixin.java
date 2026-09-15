package fr.hardel.leafs.mixin.compat.mininggadgets;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.SynchronizedArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;

/** Direwolf20-MC/MiningGadgets, durability sync list. Mining fills it from the player's region; the server tick sends and clears it. */
@Pseudo
@Mixin(targets = "com.direwolf20.mininggadgets.common.events.ServerTickHandler")
public abstract class ServerTickHandlerMixin {

    @WrapOperation(method = "<clinit>", at = @At(value = "NEW", target = "java/util/ArrayList"))
    private static <E> ArrayList<E> leafs$sharedUpdateList(Operation<ArrayList<E>> original) {
        return new SynchronizedArrayList<>();
    }
}
