package fr.hardel.leafs.mixin.entity;

import fr.hardel.excess.ConcurrentLong2ObjectMap;
import fr.hardel.excess.ConcurrentOrderedLongSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Concurrent section index. 42 is the SectionPos x-field shift: vanilla range queries never span two x values, so each one reads a single stripe. */
@Mixin(EntitySectionStorage.class)
public abstract class EntitySectionStorageMixin<T extends EntityAccess> {

    @Mutable
    @Shadow
    @Final
    private Long2ObjectMap<EntitySection<T>> sections;

    @Mutable
    @Shadow
    @Final
    private LongSortedSet sectionIds;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$swapForConcurrentFacades(CallbackInfo callbackInfo) {
        this.sections = new ConcurrentLong2ObjectMap<>();
        this.sectionIds = new ConcurrentOrderedLongSet(42);
    }
}
