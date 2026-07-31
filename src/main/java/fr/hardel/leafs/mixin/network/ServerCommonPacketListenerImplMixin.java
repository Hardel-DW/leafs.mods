package fr.hardel.leafs.mixin.network;

import fr.hardel.leafs.network.CommonListenerConnectionAccess;
import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Exposes the protected connection to network/ (region-side connection ticking). */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin implements CommonListenerConnectionAccess {

    @Shadow
    @Final
    protected Connection connection;

    @Override
    public Connection leafs$connection() {
        return connection;
    }
}
