package fr.hardel.leafs.mixin.compat.theurgy;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.SynchronizedObject2ObjectOpenHashMap;
import fr.hardel.excess.SynchronizedObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** klikli-dev/theurgy, logistics graph. Leaf nodes add and remove themselves from their region; other regions read the networks meanwhile. */
@Pseudo
@Mixin(targets = "com.klikli_dev.theurgy.logistics.Logistics")
public abstract class LogisticsMixin {

    @WrapOperation(method = "<init>", at = @At(value = "NEW", target = "it/unimi/dsi/fastutil/objects/ObjectOpenHashSet"))
    private static <E> ObjectOpenHashSet<E> leafs$sharedGraphNodes(Operation<ObjectOpenHashSet<E>> original) {
        return new SynchronizedObjectOpenHashSet<>();
    }

    @WrapOperation(method = "<init>", at = @At(value = "NEW", target = "it/unimi/dsi/fastutil/objects/Object2ObjectOpenHashMap"))
    private static <K, V> Object2ObjectOpenHashMap<K, V> leafs$sharedNetworks(Operation<Object2ObjectOpenHashMap<K, V>> original) {
        return new SynchronizedObject2ObjectOpenHashMap<>();
    }
}
