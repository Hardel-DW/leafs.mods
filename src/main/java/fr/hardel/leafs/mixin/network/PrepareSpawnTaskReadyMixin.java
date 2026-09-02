package fr.hardel.leafs.mixin.network;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.global.CommandEngine;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Hook only: the whole vanilla spawn, placement, pearls and vehicle in order, as one head execution of the server thread. */
@Mixin(targets = "net.minecraft.server.network.config.PrepareSpawnTask$Ready")
public abstract class PrepareSpawnTaskReadyMixin {

    @Shadow
    @Final
    private ServerLevel spawnLevel;

    @WrapMethod(method = "spawn")
    private ServerPlayer leafs$spawnAsHead(Connection connection, CommonListenerCookie cookie, Operation<ServerPlayer> original) {
        return CommandEngine.runHead(spawnLevel.getServer(), null, () -> original.call(connection, cookie));
    }
}
