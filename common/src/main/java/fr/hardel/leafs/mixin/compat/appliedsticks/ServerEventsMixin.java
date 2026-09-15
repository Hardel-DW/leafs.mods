package fr.hardel.leafs.mixin.compat.appliedsticks;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.SynchronizedHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashMap;

/** benbenlaw/appliedsticks, per player positions. Every player tick writes the map from the player's region. */
@Pseudo
@Mixin(targets = "com.benbenlaw.appliedsticks.event.ServerEvents")
public abstract class ServerEventsMixin {

    @WrapOperation(method = "<clinit>", at = @At(value = "NEW", target = "java/util/HashMap"))
    private static <K, V> HashMap<K, V> leafs$sharedLastSentPositions(Operation<HashMap<K, V>> original) {
        return new SynchronizedHashMap<>();
    }
}
