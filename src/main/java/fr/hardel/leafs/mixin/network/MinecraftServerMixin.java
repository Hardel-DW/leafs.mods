package fr.hardel.leafs.mixin.network;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.network.RegionNetworkTick;
import fr.hardel.leafs.ticking.ServerLevelRegionAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The send-chunks loop: a region-owned player sends from his region's tick. The stale-player
 * fallback sends under the level exclusion, because the chunks it serializes belong to regions.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    @WrapOperation(method = "tickChildren", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/PlayerChunkSender;sendNextChunks(Lnet/minecraft/server/level/ServerPlayer;)V"))
    private void leafs$sendChunksOnTheOwner(PlayerChunkSender sender, ServerPlayer player, Operation<Void> original) {
        if (RegionNetworkTick.ownedByRegion(player.connection)) {
            return;
        }

        ((ServerLevelRegionAccess) player.level()).leafs$regions().ownership().runExclusive(() -> original.call(sender, player));
    }
}
