package fr.hardel.leafs.mixin.compat.reliquary;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.SynchronizedHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashMap;

/** P3pp3rF1y/Reliquary, per player flight status. Every player tick writes the map from the player's region. */
@Pseudo
@Mixin(targets = "reliquary.handler.CommonEventHandler")
public abstract class CommonEventHandlerMixin {

    @WrapOperation(method = "<clinit>", at = @At(value = "NEW", target = "java/util/HashMap"))
    private static <K, V> HashMap<K, V> leafs$sharedPlayersFlightStatus(Operation<HashMap<K, V>> original) {
        return new SynchronizedHashMap<>();
    }
}
