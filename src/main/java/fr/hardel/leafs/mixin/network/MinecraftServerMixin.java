package fr.hardel.leafs.mixin.network;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.network.OrphanNetworkSweep;
import fr.hardel.leafs.network.RegionNetworkTick;
import fr.hardel.leafs.ticking.TickingManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/** The send-chunks section: a region-owned player sends and flushes from his region, the global loop keeps only the orphans. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    @Unique
    private final OrphanNetworkSweep leafs$sendSweep = new OrphanNetworkSweep();

    @WrapOperation(method = "tickChildren", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;getPlayers()Ljava/util/List;", ordinal = 0))
    private List<ServerPlayer> leafs$suspendOnlyOrphans(PlayerList playerList, Operation<List<ServerPlayer>> original) {
        return leafs$sendSweep.captureOrphans(original.call(playerList));
    }

    /** The vanilla send loop empties: the sweep already sent and resumed its capture. */
    @WrapOperation(method = "tickChildren", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;getPlayers()Ljava/util/List;", ordinal = 1))
    private List<ServerPlayer> leafs$sendChunksGroupedByLevel(PlayerList playerList, Operation<List<ServerPlayer>> original) {
        leafs$sendSweep.sendGrouped();
        return List.of();
    }

    /** Every save writes each region-owned player from his region's epoch walk; the global pass keeps only the players no region ticks, and every player once the pool stopped. */
    @WrapOperation(method = "saveEverything", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;saveAll()V"))
    private void leafs$savePlayersOnTheirRegions(PlayerList playerList, Operation<Void> original) {
        if (TickingManager.of((MinecraftServer) (Object) this).halted()) {
            original.call(playerList);
            return;
        }

        for (ServerPlayer player : playerList.getPlayers()) {
            if (!RegionNetworkTick.ownedByRegion(player)) {
                playerList.save(player);
            }
        }
    }

}
