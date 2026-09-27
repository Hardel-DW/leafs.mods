package fr.hardel.leafs.mixin.chunk;

import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * The lock sits on the acquire and release calls of the public methods, because Lithium overwrites acquire and release. Containers share a fixed set
 * of locks by identity hash: a lock of its own weighed 48 bytes on every container of every loaded chunk. No locked method reaches another
 * container, so a shared lock never deadlocks.
 */
@Mixin(PalettedContainer.class)
public abstract class PalettedContainerMixin {

    @Unique
    private static final ReentrantLock[] LEAFS$LOCKS = Stream.generate(ReentrantLock::new).limit(4096).toArray(ReentrantLock[]::new);

    @Inject(method = {"set(IIILjava/lang/Object;)V", "read", "write"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/PalettedContainer;acquire()V"))
    private void leafs$lock(CallbackInfo callbackInfo) {
        leafs$sharedLock().lock();
    }

    @Inject(method = {"getAndSet(IIILjava/lang/Object;)Ljava/lang/Object;", "pack"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/PalettedContainer;acquire()V"))
    private void leafs$lockForAResult(CallbackInfoReturnable<?> callbackInfo) {
        leafs$sharedLock().lock();
    }

    @Inject(method = {"set(IIILjava/lang/Object;)V", "read", "write"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/PalettedContainer;release()V", shift = At.Shift.AFTER))
    private void leafs$unlock(CallbackInfo callbackInfo) {
        leafs$sharedLock().unlock();
    }

    @Inject(method = {"getAndSet(IIILjava/lang/Object;)Ljava/lang/Object;", "pack"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/PalettedContainer;release()V", shift = At.Shift.AFTER))
    private void leafs$unlockForAResult(CallbackInfoReturnable<?> callbackInfo) {
        leafs$sharedLock().unlock();
    }

    @Unique
    private ReentrantLock leafs$sharedLock() {
        return LEAFS$LOCKS[System.identityHashCode(this) & LEAFS$LOCKS.length - 1];
    }
}
