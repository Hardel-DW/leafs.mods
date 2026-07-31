package fr.hardel.leafs.network;

import net.minecraft.network.Connection;

/** Implemented onto {@code ServerCommonPacketListenerImpl} by mixin: exposes its protected connection. */
public interface CommonListenerConnectionAccess {

    Connection leafs$connection();
}
