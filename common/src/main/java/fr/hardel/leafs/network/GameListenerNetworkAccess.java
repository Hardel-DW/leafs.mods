package fr.hardel.leafs.network;

/** Implemented onto {@code ServerGamePacketListenerImpl} by mixin. */
public interface GameListenerNetworkAccess {

    PlayerPacketQueue leafs$inboundQueue();
}
