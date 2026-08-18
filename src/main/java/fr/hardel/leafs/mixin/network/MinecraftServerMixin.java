package fr.hardel.leafs.mixin.network;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import fr.hardel.leafs.entity.ServerLevelEntityAccess;
import fr.hardel.leafs.network.OrphanNetworkSweep;
import fr.hardel.leafs.ticking.PauseBatch;
import fr.hardel.leafs.ticking.TickingManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * The send-chunks section: a region-owned player sends and flushes from his region's tick, the
 * global loop keeps only the orphans, grouped by level under one exclusion each.
 */
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

    /**
     * The periodic autosave saves each region-owned player from his region's epoch walk; the global
     * pass keeps only the players no region ticks. A flush or forced save keeps the vanilla full pass.
     */
    @WrapOperation(method = "saveEverything", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;saveAll()V"))
    private void leafs$autosavePlayersOnTheirRegions(PlayerList playerList, Operation<Void> original, @Local(argsOnly = true, ordinal = 1) boolean flush, @Local(argsOnly = true, ordinal = 2) boolean force) {
        if (flush || force) {
            original.call(playerList);
            return;
        }

        for (ServerPlayer player : playerList.getPlayers()) {
            if (!((ServerLevelEntityAccess) player.level()).leafs$entityLists().owns(player)) {
                playerList.save(player);
            }
        }
    }

    /** Disconnections are detected in this tick; the batch makes a whole wave share one region pause. */
    @WrapMethod(method = "tickConnection")
    private void leafs$batchTeardownPauses(Operation<Void> original) {
        PauseBatch batch = TickingManager.of((MinecraftServer) (Object) this).pauseBatch();
        batch.open();
        try {
            original.call();
        } finally {
            batch.close();
        }
    }
}
