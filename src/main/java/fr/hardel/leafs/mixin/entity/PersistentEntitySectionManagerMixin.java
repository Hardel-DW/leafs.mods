package fr.hardel.leafs.mixin.entity;

import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Every region that loads or spawns an entity writes this set. */
@Mixin(PersistentEntitySectionManager.class)
public abstract class PersistentEntitySectionManagerMixin {

    @Mutable
    @Shadow
    @Final
    private Set<UUID> knownUuids;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$swapForConcurrentFacade(CallbackInfo callbackInfo) {
        this.knownUuids = ConcurrentHashMap.newKeySet();
    }
}
