package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.Strategy;
import org.spongepowered.asm.mixin.Mixin;

/** Reads ride vanilla's volatile snapshot; writes and serializations take the monitor (IO packs while the region writes), so vanilla's detector never trips. */
@Mixin(PalettedContainer.class)
public abstract class PalettedContainerMixin {

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
