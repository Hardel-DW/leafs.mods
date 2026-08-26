package fr.hardel.leafs.mixin.world;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.ConcurrentInt2ObjectMap;
import fr.hardel.leafs.world.RaidTickerAccess;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raids;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Raids are created from player ticks on the regions; the server thread keeps the map, each raid ticks on the owner of its centre. */
@Mixin(Raids.class)
public abstract class RaidsMixin {

    @Mutable
    @Shadow
    @Final
    private Int2ObjectMap<Raid> raidMap;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$concurrentRaidMap(CallbackInfo callbackInfo) {
        Int2ObjectMap<Raid> concurrent = new ConcurrentInt2ObjectMap<>();
        concurrent.putAll(this.raidMap);
        this.raidMap = concurrent;
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/raid/Raid;tick(Lnet/minecraft/server/level/ServerLevel;)V"))
    private void leafs$raidTicksAsAnchored(Raid raid, ServerLevel level, Operation<Void> original) {
        ((RaidTickerAccess) raid).leafs$ensureTicker(level);
    }
}
