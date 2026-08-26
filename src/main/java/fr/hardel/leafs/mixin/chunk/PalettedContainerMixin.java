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

/** Reads ride vanilla's volatile snapshot; writes and serializations take the monitor (IO packs while the region writes). The crashing detector is disabled. */
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
