package fr.hardel.leafs.mixin.entity;

import fr.hardel.leafs.entity.ConcurrentInt2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityLookup;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Facade swap #34 - justifications in Modules.md entity/. */
@Mixin(EntityLookup.class)
public abstract class EntityLookupMixin<T extends EntityAccess> {

    @Mutable
    @Shadow
    @Final
    private Int2ObjectMap<T> byId;

    @Mutable
    @Shadow
    @Final
    private Map<UUID, T> byUuid;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$swapForConcurrentFacades(CallbackInfo callbackInfo) {
        this.byId = new ConcurrentInt2ObjectMap<>();
        this.byUuid = new ConcurrentHashMap<>();
    }
}
