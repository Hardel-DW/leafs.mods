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

/** The send-chunks section and the player saves belong to the regions: every player is owned, the global loop sees nobody. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    @WrapOperation(method = "tickChildren", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;getPlayers()Ljava/util/List;"))
    private List<ServerPlayer> leafs$regionsSendChunks(PlayerList playerList, Operation<List<ServerPlayer>> original) {
        return List.of();
    }

    /** The periodic save writes each player from his region's epoch walk; a flush is head work, every region held, then vanilla's pass, which also survives once the pool stopped. */
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

        RegionBorrow.hold(borrow -> {
            for (ServerLevel level : server.getAllLevels()) {
                borrow.borrowAll(LevelRegions.of(level));
            }

            original.call(playerList);
            return null;
        });
    }
}
