package fr.hardel.leafs.mixin.entity;

import fr.hardel.excess.ConcurrentObject2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import net.minecraft.server.level.PlayerMap;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Written by joins, leaves and moves on region threads, read by every region's spawn pass. */
@Mixin(PlayerMap.class)
public abstract class PlayerMapMixin {

    @Mutable
    @Shadow
    @Final
    private Object2BooleanMap<ServerPlayer> players;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$swapForConcurrentFacade(CallbackInfo callbackInfo) {
        this.players = new ConcurrentObject2BooleanMap<>();
    }
}
