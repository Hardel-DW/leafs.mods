package fr.hardel.leafs.mixin.network;

import fr.hardel.leafs.network.GameListenerNetworkAccess;
import fr.hardel.leafs.network.PlayerPacketQueue;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Carries the player's inbound queue; safely published through Connection's volatile listener field. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin implements GameListenerNetworkAccess {

    @Unique
    private final PlayerPacketQueue leafs$inboundQueue = new PlayerPacketQueue();

    @Override
    public PlayerPacketQueue leafs$inboundQueue() {
        return leafs$inboundQueue;
    }
}
