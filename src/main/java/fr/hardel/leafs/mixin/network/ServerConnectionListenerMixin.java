package fr.hardel.leafs.mixin.network;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.network.PacketRouting;
import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerConnectionListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Play connections of rostered players tick on their unit; the global loop keeps everything else. */
@Mixin(ServerConnectionListener.class)
public abstract class ServerConnectionListenerMixin {

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;tick()V"))
    private void leafs$skipRegionTickedConnections(Connection connection, Operation<Void> original) {
        if (!PacketRouting.ticksOnRegion(connection)) {
            original.call(connection);
        }
    }
}
