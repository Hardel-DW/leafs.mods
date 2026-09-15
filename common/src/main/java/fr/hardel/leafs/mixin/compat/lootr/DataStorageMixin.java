package fr.hardel.leafs.mixin.compat.lootr;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.SynchronizedHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashMap;

/** LootrMinecraft/Lootr#895. The section saved data type cache is filled from getUpdateTag, which every chunk owner runs. */
@Pseudo
@Mixin(targets = "noobanidus.mods.lootr.common.data.DataStorage")
public abstract class DataStorageMixin {

    @WrapOperation(method = "<clinit>", at = @At(value = "NEW", target = "java/util/HashMap"))
    private static <K, V> HashMap<K, V> leafs$sharedSectionSavedData(Operation<HashMap<K, V>> original) {
        return new SynchronizedHashMap<>();
    }
}
