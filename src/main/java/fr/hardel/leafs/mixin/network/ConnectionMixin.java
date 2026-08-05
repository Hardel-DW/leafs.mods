package fr.hardel.leafs.mixin.network;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.network.RegionNetworkTick;
import net.minecraft.network.Connection;
import net.minecraft.network.TickablePacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Hook only - network/RegionNetworkTick splits the tick: transport stays here, the listener half runs on the owning region. */
@Mixin(Connection.class)
public abstract class ConnectionMixin {

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/TickablePacketListener;tick()V"))
    private void leafs$listenerHalfByOwner(TickablePacketListener listener, Operation<Void> original) {
        RegionNetworkTick.tickListenerGlobally(listener, () -> original.call(listener));
    }
}
