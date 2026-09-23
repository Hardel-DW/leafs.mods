package fr.hardel.leafs.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Pseudo
@Mixin(targets = {
    // Commoble/exmachina, mechanical buffer, fed by enqueue on every NeighborNotifyEvent from any thread while tick swaps and walks it.
    "net.commoble.exmachina.internal.mechanical.MechanicalGraphBuffer",
    // Commoble/exmachina, signal buffer, fed by enqueue on every VanillaGameEvent from any thread while tick swaps and walks it.
    "net.commoble.exmachina.internal.signal.SignalGraphBuffer"
})
public abstract class GraphBufferMixin {

    @WrapOperation(method = "enqueue", at = @At(value = "INVOKE", target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"))
    private static Object leafs$concurrentLevelPositions(Map<?, ?> positions, Object level, Function<?, ?> mapping, Operation<Object> original) {
        return original.call(positions, level, (Function<ResourceKey<Level>, Set<BlockPos>>) _ -> ConcurrentHashMap.newKeySet());
    }
}
