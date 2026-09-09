package fr.hardel.leafs.mixin.global;

import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Watched by players of every region, joined and left from other threads while a boss ticks its progress: the player set goes concurrent. */
@Mixin(ServerBossEvent.class)
public abstract class ServerBossEventMixin {

    @Mutable
    @Shadow
    @Final
    private Set<ServerPlayer> players;

    @Mutable
    @Shadow
    @Final
    private Set<ServerPlayer> unmodifiablePlayers;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$concurrentPlayers(CallbackInfo callbackInfo) {
        this.players = ConcurrentHashMap.newKeySet();
        this.unmodifiablePlayers = Collections.unmodifiableSet(this.players);
    }
}
