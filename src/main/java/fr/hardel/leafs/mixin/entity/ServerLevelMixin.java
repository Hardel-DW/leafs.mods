package fr.hardel.leafs.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.ConcurrentInt2ObjectMap;
import fr.hardel.leafs.entity.EntityManagerAccess;
import fr.hardel.leafs.chunk.RegionChunkAccess;
import fr.hardel.leafs.entity.EntityTeleports;
import fr.hardel.leafs.entity.RegionEntityPersistence;
import fr.hardel.leafs.entity.ServerLevelEntityAccess;
import fr.hardel.leafs.world.RegionWorldData;
import fr.hardel.leafs.world.WorldTickContext;
import fr.hardel.leafs.ownership.RegionContext;
import fr.hardel.leafs.global.SharedStateMonitor;
import fr.hardel.leafs.ticking.TickingBinding;
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

/** Facade swap (dragonParts, players COW), teleport routing, passenger membership from the region photo. */
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
    private EntityTeleports leafs$entityTeleports;

    @Unique
    private RegionEntityPersistence leafs$entityPersistence;

    @Override
    public EntityTeleports leafs$entityTeleports() {
        return leafs$entityTeleports;
    }

    @Override
    public RegionEntityPersistence leafs$entityPersistence() {
        return leafs$entityPersistence;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$swapForConcurrentFacade(CallbackInfo callbackInfo) {
        this.dragonParts = new ConcurrentInt2ObjectMap<>();
        this.players = new CopyOnWriteArrayList<>();
        ServerLevel self = (ServerLevel) (Object) this;
        this.leafs$entityTeleports = new EntityTeleports(self, TickingBinding::of);
        EntityManagerAccess manager = (EntityManagerAccess) self.entityManager;
        this.leafs$entityPersistence = new RegionEntityPersistence(self, manager, () -> RegionChunkAccess.scheduling(self.getChunkSource().chunkMap).mailbox().drainAll());
        manager.leafs$bindPersistence(this.leafs$entityPersistence);
    }

    /** A passenger ticks with its vehicle when the region's photo holds it; the vanilla list stays empty by routing. */
    @WrapOperation(method = "tickPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/EntityTickList;contains(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean leafs$containsInRegionPhoto(EntityTickList instance, Entity entity, Operation<Boolean> original) {
        RegionWorldData data = WorldTickContext.activeFor((ServerLevel) (Object) this);
        return data != null && data.entities().contains(entity);
    }

    /** From a region of another level the add hops to this level's owner of the position; here two regions serialize on the level-wide player maps. */
    @WrapMethod(method = "addPlayer")
    private void leafs$addPlayerOnTheOwner(ServerPlayer player, Operation<Void> original) {
        if (leafs$fromAnotherLevel()) {
            leafs$onOwnerOf(player, () -> original.call(player));
            return;
        }

        SharedStateMonitor.run(this, () -> original.call(player));
    }

    /** A hop answers true: the duplicate-UUID check happens at delivery, on the owner. */
    @WrapMethod(method = "addEntity")
    private boolean leafs$addEntityOnTheOwner(Entity entity, Operation<Boolean> original) {
        if (leafs$fromAnotherLevel()) {
            leafs$onOwnerOf(entity, () -> original.call(entity));
            return true;
        }

        return original.call(entity);
    }

    @WrapMethod(method = "removePlayerImmediately")
    private void leafs$removePlayerOnTheOwner(ServerPlayer player, Entity.RemovalReason reason, Operation<Void> original) {
        if (leafs$fromAnotherLevel()) {
            leafs$onOwnerOf(player, () -> original.call(player, reason));
            return;
        }

        SharedStateMonitor.run(this, () -> original.call(player, reason));
    }

    @Unique
    private boolean leafs$fromAnotherLevel() {
        String here = ((ServerLevel) (Object) this).dimension().identifier().toString();
        return RegionContext.current() instanceof RegionContext.Region(long _, String dimension) && !dimension.equals(here);
    }

    @Unique
    private void leafs$onOwnerOf(Entity entity, Runnable task) {
        ServerLevel self = (ServerLevel) (Object) this;
        TickingBinding.of(self).toOwner(entity.chunkPosition().x(), entity.chunkPosition().z(), task);
    }

}
