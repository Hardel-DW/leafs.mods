package fr.hardel.leafs.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.capabilities.CapabilityListenerHolder;
import net.neoforged.neoforge.capabilities.ICapabilityInvalidationListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.locks.ReentrantLock;

@Mixin(CapabilityListenerHolder.class)
public abstract class CapabilityListenerHolderShim {
    @Unique
    private final ReentrantLock leafs$lock = new ReentrantLock();

    @WrapMethod(method = "addListener")
    private void leafs$lockedAddListener(BlockPos pos, ICapabilityInvalidationListener listener, Operation<Void> original) {
        leafs$lock.lock();
        try {
            original.call(pos, listener);
        } finally {
            leafs$lock.unlock();
        }
    }

    @WrapMethod(method = "invalidatePos")
    private void leafs$lockedInvalidatePos(BlockPos pos, Operation<Void> original) {
        leafs$lock.lock();
        try {
            original.call(pos);
        } finally {
            leafs$lock.unlock();
        }
    }

    @WrapMethod(method = "invalidateChunk")
    private void leafs$lockedInvalidateChunk(ChunkPos chunkPos, Operation<Void> original) {
        leafs$lock.lock();
        try {
            original.call(chunkPos);
        } finally {
            leafs$lock.unlock();
        }
    }

    @WrapMethod(method = "clean")
    private void leafs$cleanWhenFree(Operation<Void> original) {
        if (!leafs$lock.tryLock()) {
            return;
        }

        try {
            original.call();
        } finally {
            leafs$lock.unlock();
        }
    }
}
