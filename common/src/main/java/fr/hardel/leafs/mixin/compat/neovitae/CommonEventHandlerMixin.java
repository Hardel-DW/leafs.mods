package fr.hardel.leafs.mixin.compat.neovitae;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.SynchronizedHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashMap;

/** BreakinBlocks/NeoVitae, per player maps. Player ticks and falls write the maps from the player's region. */
@Pseudo
@Mixin(targets = "com.breakinblocks.neovitae.common.event.CommonEventHandler")
public abstract class CommonEventHandlerMixin {

    @WrapOperation(method = "<clinit>", at = @At(value = "NEW", target = "java/util/HashMap"))
    private static <K, V> HashMap<K, V> leafs$sharedPlayerMaps(Operation<HashMap<K, V>> original) {
        return new SynchronizedHashMap<>();
    }
}
