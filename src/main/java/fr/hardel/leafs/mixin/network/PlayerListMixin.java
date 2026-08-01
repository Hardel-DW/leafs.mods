package fr.hardel.leafs.mixin.network;

import fr.hardel.leafs.ticking.LeafsServerAccess;
import fr.hardel.leafs.ticking.TickingManager;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Player lists become safe for cross-region reads (broadcasts, PlayerLookup); placement runs on the
 * unit owning the player's spawn level — global builds the player, the region places it.
 */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {

    @Mutable
    @Shadow
    @Final
    private List<ServerPlayer> players;

    @Mutable
    @Shadow
    @Final
    private Map<UUID, ServerPlayer> playersByUUID;

    @Shadow
    public abstract MinecraftServer getServer();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$concurrentPlayerLists(CallbackInfo callbackInfo) {
        this.players = new CopyOnWriteArrayList<>();
        this.playersByUUID = new ConcurrentHashMap<>();
    }

    /** Attached mode: the server thread owns every level between unit ticks (the integrated server pauses while empty, so a deferred placement would never run — M11 revisits with real region ownership). */
    @Inject(method = "placeNewPlayer", at = @At("HEAD"), cancellable = true)
    private void leafs$placeOnOwningUnit(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo callbackInfo) {
        TickingManager ticking = ((LeafsServerAccess) this.getServer()).leafs$ticking();
        if (ticking.currentThreadOwns(player.level()) || this.getServer().isSameThread()) {
            return;
        }

        ticking.submitToLevel(player.level(), () -> ((PlayerList) (Object) this).placeNewPlayer(connection, player, cookie));
        callbackInfo.cancel();
    }
}
