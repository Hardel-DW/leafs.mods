package fr.hardel.leafs.mixin.network;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.network.RegionNetworkTick;
import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerConnectionListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerConnectionListener.class)
public abstract class ServerConnectionListenerMixin {

    // The server thread keeps the list and the disconnections: its tick must not grow with the players.
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;tick()V"))
    private void leafs$aRegionTicksTheConnectionsOfItsPlayers(Connection connection, Operation<Void> original) {
        if (!RegionNetworkTick.tickedByARegion(connection)) {
            original.call(connection);
        }
    }
}
