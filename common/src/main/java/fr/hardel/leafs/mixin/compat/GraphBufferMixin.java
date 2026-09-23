package fr.hardel.leafs.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Pseudo
@Mixin(targets = {
    // Commoble/exmachina, mechanical buffer, fed by enqueue on every NeighborNotifyEvent from any thread while tick swaps and walks it.
    "net.commoble.exmachina.internal.mechanical.MechanicalGraphBuffer",
    // Commoble/exmachina, signal buffer, fed by enqueue on every VanillaGameEvent from any thread while tick swaps and walks it.
    "net.commoble.exmachina.internal.signal.SignalGraphBuffer"
})
public abstract class GraphBufferMixin {

    @WrapMethod(method = "lambda$enqueue$0")
    private static Set<BlockPos> leafs$concurrentLevelPositions(ResourceKey<Level> level, Operation<Set<BlockPos>> original) {
        return ConcurrentHashMap.newKeySet();
    }
}
