package fr.hardel.leafs.mixin.network;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.ticking.TickingManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/** The send-chunks section and the player saves belong to the regions: every player is owned, the global loop sees nobody. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    @WrapOperation(method = "tickChildren", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;getPlayers()Ljava/util/List;"))
    private List<ServerPlayer> leafs$regionsSendChunks(PlayerList playerList, Operation<List<ServerPlayer>> original) {
        return List.of();
    }

    /** Every save writes each player from his region's epoch walk; the global pass only survives once the pool stopped. */
    @WrapOperation(method = "saveEverything", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;saveAll()V"))
    private void leafs$savePlayersOnTheirRegions(PlayerList playerList, Operation<Void> original) {
        if (TickingManager.of((MinecraftServer) (Object) this).halted()) {
            original.call(playerList);
        }
    }
}
