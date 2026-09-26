package fr.hardel.leafs.mixin.chunk;

import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.locks.ReentrantLock;

/** The lock sits on the acquire and release calls of the public methods, because Lithium overwrites acquire and release. */
@Mixin(PalettedContainer.class)
public abstract class PalettedContainerMixin {

    @Unique
    private final ReentrantLock leafs$lock = new ReentrantLock();

    @Inject(method = {"set(IIILjava/lang/Object;)V", "read", "write"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/PalettedContainer;acquire()V"))
    private void leafs$lock(CallbackInfo callbackInfo) {
        leafs$lock.lock();
    }

    @Inject(method = {"getAndSet(IIILjava/lang/Object;)Ljava/lang/Object;", "pack"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/PalettedContainer;acquire()V"))
    private void leafs$lockForAResult(CallbackInfoReturnable<?> callbackInfo) {
        leafs$lock.lock();
    }

    @Inject(method = {"set(IIILjava/lang/Object;)V", "read", "write"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/PalettedContainer;release()V", shift = At.Shift.AFTER))
    private void leafs$unlock(CallbackInfo callbackInfo) {
        leafs$lock.unlock();
    }

    @Inject(method = {"getAndSet(IIILjava/lang/Object;)Ljava/lang/Object;", "pack"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/PalettedContainer;release()V", shift = At.Shift.AFTER))
    private void leafs$unlockForAResult(CallbackInfoReturnable<?> callbackInfo) {
        leafs$lock.unlock();
    }
}
