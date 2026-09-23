package fr.hardel.leafs.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.SynchronizedHashMap;
import fr.hardel.excess.SynchronizedHashSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashMap;
import java.util.HashSet;

@Pseudo
@Mixin(targets = {
    // Commoble/exmachina, mechanical buffer, fed by enqueue on every NeighborNotifyEvent from any thread while tick swaps and walks it.
    "net.commoble.exmachina.internal.mechanical.MechanicalGraphBuffer",
    // Commoble/exmachina, signal buffer, fed by enqueue on every VanillaGameEvent from any thread while tick swaps and walks it.
    "net.commoble.exmachina.internal.signal.SignalGraphBuffer"
})
public abstract class GraphBufferMixin {

    @WrapOperation(method = {"<init>", "tick"}, at = @At(value = "NEW", target = "java/util/HashMap"))
    private static <K, V> HashMap<K, V> leafs$synchronizedPositions(Operation<HashMap<K, V>> original) {
        return new SynchronizedHashMap<>();
    }

    @WrapOperation(method = "lambda$enqueue$0", at = @At(value = "NEW", target = "java/util/HashSet"))
    private static <E> HashSet<E> leafs$synchronizedLevelPositions(Operation<HashSet<E>> original) {
        return new SynchronizedHashSet<>();
    }
}
