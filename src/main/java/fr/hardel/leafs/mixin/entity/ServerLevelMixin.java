package fr.hardel.leafs.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.entity.ConcurrentInt2ObjectMap;
import fr.hardel.leafs.entity.EntityTeleports;
import fr.hardel.leafs.entity.LevelEntityLists;
import fr.hardel.leafs.entity.ServerLevelEntityAccess;
import fr.hardel.leafs.ticking.LevelBindings;
import fr.hardel.leafs.ticking.LevelOwnership;
import fr.hardel.leafs.ticking.ServerLevelRegionAccess;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.level.entity.EntityTickList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Facade swap #38 (dragonParts, players COW) plus the carriers of the per-region entity lists and the
 * teleport routing; player add/remove entries take this level's exclusion at the mutation boundary
 * (the decided player-scoped gate). Not #3b (ticking/) or #9 (world/) on the same target.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin implements ServerLevelEntityAccess {

    @Mutable
    @Shadow
    @Final
    private Int2ObjectMap<EnderDragonPart> dragonParts;

    @Mutable
    @Shadow
    @Final
    private List<ServerPlayer> players;

    @Unique
    private LevelEntityLists leafs$entityLists;

    @Unique
    private EntityTeleports leafs$entityTeleports;

    @Override
    public LevelEntityLists leafs$entityLists() {
        return leafs$entityLists;
    }

    @Override
    public EntityTeleports leafs$entityTeleports() {
        return leafs$entityTeleports;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$swapForConcurrentFacade(CallbackInfo callbackInfo) {
        this.dragonParts = new ConcurrentInt2ObjectMap<>();
        this.players = new CopyOnWriteArrayList<>();
        this.leafs$entityLists = new LevelEntityLists();
        this.leafs$entityTeleports = LevelBindings.entityTeleports((ServerLevel) (Object) this);
    }

    /** Riding passengers are looked up in the owning unit's list; the vanilla list stays empty by routing. */
    @WrapOperation(method = "tickPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/EntityTickList;contains(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean leafs$containsInOwningUnit(EntityTickList instance, Entity entity, Operation<Boolean> original) {
        return leafs$entityLists.containsTicking(entity);
    }

    @WrapMethod(method = "addPlayer")
    private void leafs$addPlayerUnderExclusion(ServerPlayer player, Operation<Void> original) {
        leafs$ownership().runExclusive(() -> original.call(player));
    }

    @WrapMethod(method = "addEntity")
    private boolean leafs$addEntityUnderExclusion(Entity entity, Operation<Boolean> original) {
        return leafs$ownership().callExclusive(() -> original.call(entity));
    }

    @WrapMethod(method = "removePlayerImmediately")
    private void leafs$removePlayerUnderExclusion(ServerPlayer player, Entity.RemovalReason reason, Operation<Void> original) {
        leafs$ownership().runExclusive(() -> original.call(player, reason));
    }

    @Unique
    private LevelOwnership leafs$ownership() {
        return ((ServerLevelRegionAccess) this).leafs$regions().ownership();
    }
}
