package fr.hardel.leafs.mixin.compat.auroral;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.SynchronizedHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashMap;

/** BreakinBlocks/Auroral, per player positions. Dimension changes write the map from the player's region. */
@Pseudo
@Mixin(targets = "com.breakinblocks.auroral.events.PlayerEventHandler")
public abstract class PlayerEventHandlerMixin {

    @WrapOperation(method = "<clinit>", at = @At(value = "NEW", target = "java/util/HashMap"))
    private static <K, V> HashMap<K, V> leafs$sharedPreTransitPositions(Operation<HashMap<K, V>> original) {
        return new SynchronizedHashMap<>();
    }
}
