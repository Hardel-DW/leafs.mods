package fr.hardel.leafs.mixin.compat.exmachina;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.SynchronizedHashMap;
import fr.hardel.excess.SynchronizedHashSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashMap;
import java.util.HashSet;

/** Commoble/exmachina, synchronized buffers. enqueue runs on every NeighborNotifyEvent, from any thread, while tick swaps and walks the buffer. The local maps of tick share the treatment, at no cost. */
@Pseudo
@Mixin(targets = "net.commoble.exmachina.internal.mechanical.MechanicalGraphBuffer")
public abstract class MechanicalGraphBufferMixin {

    @WrapOperation(method = {"<init>", "tick"}, at = @At(value = "NEW", target = "java/util/HashMap"))
    private static <K, V> HashMap<K, V> leafs$sharedPositions(Operation<HashMap<K, V>> original) {
        return new SynchronizedHashMap<>();
    }

    @WrapOperation(method = "lambda$enqueue$0", at = @At(value = "NEW", target = "java/util/HashSet"))
    private static <E> HashSet<E> leafs$sharedLevelPositions(Operation<HashSet<E>> original) {
        return new SynchronizedHashSet<>();
    }
}
