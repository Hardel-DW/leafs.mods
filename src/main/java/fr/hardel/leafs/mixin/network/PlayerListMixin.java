package fr.hardel.leafs.mixin.network;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.global.CommandEngine;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.stats.ServerStatsCounter;
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

/** Concurrent player lists for cross-region reads; the join and the leave are head executions of the server thread. */
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

    @Mutable
    @Shadow
    @Final
    private Map<UUID, ServerStatsCounter> stats;

    @Mutable
    @Shadow
    @Final
    private Map<UUID, PlayerAdvancements> advancements;

    @Shadow
    public abstract MinecraftServer getServer();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$concurrentPlayerLists(CallbackInfo callbackInfo) {
        this.players = new CopyOnWriteArrayList<>();
        this.playersByUUID = new ConcurrentHashMap<>();
        this.stats = new ConcurrentHashMap<>();
        this.advancements = new ConcurrentHashMap<>();
    }

    /** A join or a leave called from the server thread is a head execution, vanilla order kept; a region thread already owns or mails what it touches. */
    @WrapMethod(method = "placeNewPlayer")
    private void leafs$placeAsHead(Connection connection, ServerPlayer player, CommonListenerCookie cookie, Operation<Void> original) {
        CommandEngine.runHead(getServer(), player, () -> original.call(connection, player, cookie));
    }

    @WrapMethod(method = "remove")
    private void leafs$removeAsHead(ServerPlayer player, Operation<Void> original) {
        CommandEngine.runHead(getServer(), player, () -> original.call(player));
    }
}
