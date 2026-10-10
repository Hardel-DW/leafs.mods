package fr.hardel.leafs.mixin.network;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.RegionBorrow;
import fr.hardel.leafs.ticking.TickingManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    @WrapOperation(method = "tickChildren", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;getPlayers()Ljava/util/List;"))
    private List<ServerPlayer> leafs$regionsSendChunks(PlayerList playerList, Operation<List<ServerPlayer>> original) {
        return List.of();
    }

    @WrapOperation(method = "saveEverything", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;saveAll()V"))
    private void leafs$savePlayersOnTheirRegions(PlayerList playerList, Operation<Void> original, @Local(argsOnly = true, ordinal = 1) boolean flush) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        if (TickingManager.of(server).halted()) {
            original.call(playerList);
            return;
        }

        if (!flush) {
            return;
        }

        for (ServerLevel level : server.getAllLevels()) {
            RegionBorrow.lockAll(LevelRegions.of(level));
        }

        original.call(playerList);
    }
}
