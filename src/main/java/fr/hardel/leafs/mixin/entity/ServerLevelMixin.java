package fr.hardel.leafs.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.ConcurrentInt2ObjectMap;
import fr.hardel.leafs.entity.EntityManagerAccess;
import fr.hardel.leafs.entity.EntityTeleports;
import fr.hardel.leafs.entity.LevelEntityLists;
import fr.hardel.leafs.entity.RegionEntityPersistence;
import fr.hardel.leafs.entity.ServerLevelEntityAccess;
import fr.hardel.leafs.ownership.RegionContext;
import fr.hardel.leafs.ticking.LevelOwnership;
import fr.hardel.leafs.ticking.LevelRegions;
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

/** Facade swap (dragonParts, players COW), per-region entity lists, teleport routing. Player mutations take the exclusion. */
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

    @Unique
    private RegionEntityPersistence leafs$entityPersistence;

    @Override
    public LevelEntityLists leafs$entityLists() {
        return leafs$entityLists;
    }

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
        this.leafs$entityLists = new LevelEntityLists();
        ServerLevel self = (ServerLevel) (Object) this;
        this.leafs$entityTeleports = new EntityTeleports(self, TickingBinding::of);
        EntityManagerAccess manager = (EntityManagerAccess) self.entityManager;
        this.leafs$entityPersistence = new RegionEntityPersistence(self, manager, () -> LevelRegions.of(self).drainTasksInline());
        manager.leafs$bindPersistence(this.leafs$entityPersistence);
    }

    /** Riding passengers are looked up in the owning unit's list; the vanilla list stays empty by routing. */
    @WrapOperation(method = "tickPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/EntityTickList;contains(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean leafs$containsInOwningUnit(EntityTickList instance, Entity entity, Operation<Boolean> original) {
        return leafs$entityLists.containsTicking(entity);
    }

    /** From a region of another level the add hops to this level's owner of the position; here it is serialized on the level-wide player maps. */
    @WrapMethod(method = "addPlayer")
    private void leafs$addPlayerOnTheOwner(ServerPlayer player, Operation<Void> original) {
        if (leafs$fromAnotherLevel()) {
            leafs$onOwnerOf(player, () -> original.call(player));
            return;
        }

        leafs$ownership().runExclusiveSerialized(() -> original.call(player));
    }

    /** A hop answers true: the duplicate-UUID check happens at delivery, on the owner. */
    @WrapMethod(method = "addEntity")
    private boolean leafs$addEntityOnTheOwner(Entity entity, Operation<Boolean> original) {
        if (leafs$fromAnotherLevel()) {
            leafs$onOwnerOf(entity, () -> original.call(entity));
            return true;
        }

        return leafs$ownership().callExclusive(() -> original.call(entity));
    }

    @WrapMethod(method = "removePlayerImmediately")
    private void leafs$removePlayerOnTheOwner(ServerPlayer player, Entity.RemovalReason reason, Operation<Void> original) {
        if (leafs$fromAnotherLevel()) {
            leafs$onOwnerOf(player, () -> original.call(player, reason));
            return;
        }

        leafs$ownership().runExclusiveSerialized(() -> original.call(player, reason));
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

    @Unique
    private LevelOwnership leafs$ownership() {
        return LevelRegions.of((ServerLevel) (Object) this).ownership();
    }
}
