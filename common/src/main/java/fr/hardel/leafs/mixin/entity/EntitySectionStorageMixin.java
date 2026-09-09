package fr.hardel.leafs.mixin.entity;

import org.spongepowered.asm.mixin.Unique;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.SectionPos;
import fr.hardel.leafs.ticking.RegionBorrow;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.entity.LevelBoundAccess;
import fr.hardel.excess.ConcurrentLong2ObjectMap;
import fr.hardel.excess.ConcurrentOrderedLongSet;
import fr.hardel.leafs.entity.EntitySectionVisibilityAccess;
import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.Visibility;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Concurrent section index, 42 being the SectionPos x-field shift so a range query reads a single stripe; a borrower takes the regions a box covers. */
@Mixin(EntitySectionStorage.class)
public abstract class EntitySectionStorageMixin<T extends EntityAccess> implements EntitySectionVisibilityAccess, LevelBoundAccess {

    @Unique
    private ServerLevel leafs$level;

    @Mutable
    @Shadow
    @Final
    private Long2ObjectMap<EntitySection<T>> sections;

    @Mutable
    @Shadow
    @Final
    private LongSortedSet sectionIds;

    @Mutable
    @Shadow
    @Final
    private Long2ObjectFunction<Visibility> intialSectionVisibility;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$swapForConcurrentFacades(CallbackInfo callbackInfo) {
        this.sections = new ConcurrentLong2ObjectMap<>();
        this.sectionIds = new ConcurrentOrderedLongSet(42);
    }

    @Override
    public void leafs$bindInitialVisibility(Long2ObjectFunction<Visibility> visibility) {
        this.intialSectionVisibility = visibility;
    }

    @Override
    public void leafs$bindLevel(ServerLevel level) {
        leafs$level = level;
    }

    /** A box query from the borrowing server thread takes every region the box covers before it walks the sections. */
    @Inject(method = "forEachAccessibleNonEmptySection", at = @At("HEAD"))
    private void leafs$borrowTheBox(AABB box, AbortableIterationConsumer<EntitySection<T>> output, CallbackInfo callbackInfo) {
        RegionBorrow borrow = RegionBorrow.current();
        if (borrow == null || leafs$level == null) {
            return;
        }

        LevelRegions regions = LevelRegions.of(leafs$level);
        for (int chunkX = SectionPos.blockToSectionCoord(box.minX - 2.0); chunkX <= SectionPos.blockToSectionCoord(box.maxX + 2.0); chunkX++) {
            for (int chunkZ = SectionPos.blockToSectionCoord(box.minZ - 2.0); chunkZ <= SectionPos.blockToSectionCoord(box.maxZ + 2.0); chunkZ++) {
                borrow.borrow(regions, chunkX, chunkZ);
            }
        }
    }
}
