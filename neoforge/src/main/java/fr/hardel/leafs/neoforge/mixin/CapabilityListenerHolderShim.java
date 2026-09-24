package fr.hardel.leafs.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.capabilities.CapabilityListenerHolder;
import net.neoforged.neoforge.capabilities.ICapabilityInvalidationListener;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(CapabilityListenerHolder.class)
public abstract class CapabilityListenerHolderShim {

    @WrapMethod(method = "addListener")
    private void leafs$monitoredAddListener(BlockPos pos, ICapabilityInvalidationListener listener, Operation<Void> original) {
        synchronized (this) {
            original.call(pos, listener);
        }
    }

    @WrapMethod(method = "invalidatePos")
    private void leafs$monitoredInvalidatePos(BlockPos pos, Operation<Void> original) {
        synchronized (this) {
            original.call(pos);
        }
    }

    @WrapMethod(method = "invalidateChunk")
    private void leafs$monitoredInvalidateChunk(ChunkPos chunkPos, Operation<Void> original) {
        synchronized (this) {
            original.call(chunkPos);
        }
    }

    @WrapMethod(method = "clean")
    private void leafs$monitoredClean(Operation<Void> original) {
        synchronized (this) {
            original.call();
        }
    }
}
