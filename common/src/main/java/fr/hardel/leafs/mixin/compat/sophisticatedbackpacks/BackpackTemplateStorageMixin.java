package fr.hardel.leafs.mixin.compat.sophisticatedbackpacks;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.SynchronizedHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashMap;

/** P3pp3rF1y/SophisticatedBackpacks, templates. Commands write the templates from the server thread while items read them from their region. */
@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackTemplateStorage")
public abstract class BackpackTemplateStorageMixin {

    @WrapOperation(method = "<init>", at = @At(value = "NEW", target = "java/util/HashMap"))
    private static <K, V> HashMap<K, V> leafs$sharedTemplates(Operation<HashMap<K, V>> original) {
        return new SynchronizedHashMap<>();
    }
}
