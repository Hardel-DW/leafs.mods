package fr.hardel.leafs.mixin.compat.mahoutsukai;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.SynchronizedHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashMap;

/** stepsword/mahoutsukai, staff users. Every staff use writes the map from the player's region, Morgan included. */
@Pseudo
@Mixin(targets = "stepsword.mahoutsukai.item.emrys.StaffEmrys")
public abstract class StaffEmrysMixin {

    @WrapOperation(method = "<clinit>", at = @At(value = "NEW", target = "java/util/HashMap"))
    private static <K, V> HashMap<K, V> leafs$sharedStaffs(Operation<HashMap<K, V>> original) {
        return new SynchronizedHashMap<>();
    }
}
