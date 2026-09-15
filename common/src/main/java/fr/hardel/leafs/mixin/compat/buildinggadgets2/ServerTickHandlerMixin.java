package fr.hardel.leafs.mixin.compat.buildinggadgets2;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.SynchronizedHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashMap;

/** Direwolf20-MC/BuildingGadgets2, build queue. Gadget use fills the map from the player's region; the server tick walks it and removes the finished builds. */
@Pseudo
@Mixin(targets = "com.direwolf20.buildinggadgets2.common.events.ServerTickHandler")
public abstract class ServerTickHandlerMixin {

    @WrapOperation(method = "<clinit>", at = @At(value = "NEW", target = "java/util/HashMap"))
    private static <K, V> HashMap<K, V> leafs$sharedBuildMap(Operation<HashMap<K, V>> original) {
        return new SynchronizedHashMap<>();
    }
}
