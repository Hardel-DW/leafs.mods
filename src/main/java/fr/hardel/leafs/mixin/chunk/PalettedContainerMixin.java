package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.Strategy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Block state reads from any thread ride the volatile data snapshot vanilla already publishes, so
 * only the writers need care. The section has one gameplay writer at a time by region geometry, but
 * the IO worker packs and the network path writes the same container, so every mutation and
 * serialization takes the container's monitor. The ThreadingDetector crashes the second entrant
 * instead of waiting, which turns that legitimate pair into a crash: it is disabled outright.
 */
@Mixin(PalettedContainer.class)
public abstract class PalettedContainerMixin {

    @Inject(method = "acquire", at = @At("HEAD"), cancellable = true)
    private void leafs$noCrashingDetector(CallbackInfo callbackInfo) {
        callbackInfo.cancel();
    }

    @Inject(method = "release", at = @At("HEAD"), cancellable = true)
    private void leafs$noCrashingDetectorRelease(CallbackInfo callbackInfo) {
        callbackInfo.cancel();
    }

    @WrapMethod(method = "getAndSet(IIILjava/lang/Object;)Ljava/lang/Object;")
    private Object leafs$monitoredGetAndSet(int x, int y, int z, Object value, Operation<Object> original) {
        synchronized (this) {
            return original.call(x, y, z, value);
        }
    }

    @WrapMethod(method = "set(IIILjava/lang/Object;)V")
    private void leafs$monitoredSet(int x, int y, int z, Object value, Operation<Void> original) {
        synchronized (this) {
            original.call(x, y, z, value);
        }
    }

    @WrapMethod(method = "read(Lnet/minecraft/network/FriendlyByteBuf;)V")
    private void leafs$monitoredRead(FriendlyByteBuf buffer, Operation<Void> original) {
        synchronized (this) {
            original.call(buffer);
        }
    }

    @WrapMethod(method = "write(Lnet/minecraft/network/FriendlyByteBuf;)V")
    private void leafs$monitoredWrite(FriendlyByteBuf buffer, Operation<Void> original) {
        synchronized (this) {
            original.call(buffer);
        }
    }

    @WrapMethod(method = "pack(Lnet/minecraft/world/level/chunk/Strategy;)Lnet/minecraft/world/level/chunk/PalettedContainerRO$PackedData;")
    private PalettedContainerRO.PackedData<?> leafs$monitoredPack(Strategy<?> strategy, Operation<PalettedContainerRO.PackedData<?>> original) {
        synchronized (this) {
            return original.call(strategy);
        }
    }
}
