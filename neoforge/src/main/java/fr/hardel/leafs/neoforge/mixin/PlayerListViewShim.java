package fr.hardel.leafs.neoforge.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.List;

/** NeoForge hands out an unmodifiable view built over the original player list; Leafs swaps that list for a concurrent one at the end of construction, so the view follows, after the swap. */
@Mixin(value = PlayerList.class, priority = 1500)
public abstract class PlayerListViewShim {

    @Shadow
    @Final
    private List<ServerPlayer> players;

    @Mutable
    @Shadow
    @Final
    private List<ServerPlayer> playersView;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$viewTheConcurrentList(CallbackInfo callbackInfo) {
        this.playersView = Collections.unmodifiableList(this.players);
    }
}
