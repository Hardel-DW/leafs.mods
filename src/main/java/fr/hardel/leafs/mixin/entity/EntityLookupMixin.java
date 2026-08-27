package fr.hardel.leafs.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.excess.ConcurrentInt2ObjectMap;
import fr.hardel.leafs.entity.LevelBoundAccess;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.RegionBorrow;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The level's entity indexes are read and written from every region, so both maps go concurrent; a borrower takes the region of every entity handed out. */
@Mixin(EntityLookup.class)
public abstract class EntityLookupMixin<T extends EntityAccess> implements LevelBoundAccess {

    @Mutable
    @Shadow
    @Final
    private Int2ObjectMap<T> byId;

    @Mutable
    @Shadow
    @Final
    private Map<UUID, T> byUuid;

    @Unique
    private ServerLevel leafs$level;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$swapForConcurrentFacades(CallbackInfo callbackInfo) {
        this.byId = new ConcurrentInt2ObjectMap<>();
        this.byUuid = new ConcurrentHashMap<>();
    }

    @Override
    public void leafs$bindLevel(ServerLevel level) {
        leafs$level = level;
    }

    @WrapMethod(method = "getEntity(I)Lnet/minecraft/world/level/entity/EntityAccess;")
    private T leafs$borrowById(int id, Operation<T> original) {
        return leafs$borrowed(original.call(id));
    }

    @WrapMethod(method = "getEntity(Ljava/util/UUID;)Lnet/minecraft/world/level/entity/EntityAccess;")
    private T leafs$borrowByUuid(UUID uuid, Operation<T> original) {
        return leafs$borrowed(original.call(uuid));
    }

    /** The whole index, what an unbounded selector walks: every region of the level. */
    @Inject(method = "getEntities", at = @At("HEAD"))
    private <U extends T> void leafs$borrowAllForTheWalk(EntityTypeTest<T, U> type, AbortableIterationConsumer<U> consumer, CallbackInfo callbackInfo) {
        RegionBorrow borrow = RegionBorrow.current();
        if (borrow != null && leafs$level != null) {
            borrow.borrowAll(LevelRegions.of(leafs$level));
        }
    }

    @Unique
    private T leafs$borrowed(T found) {
        RegionBorrow borrow = RegionBorrow.current();
        if (borrow != null && leafs$level != null && found instanceof Entity entity) {
            ChunkPos chunk = entity.chunkPosition();
            borrow.borrow(LevelRegions.of(leafs$level), chunk.x(), chunk.z());
        }

        return found;
    }
}
