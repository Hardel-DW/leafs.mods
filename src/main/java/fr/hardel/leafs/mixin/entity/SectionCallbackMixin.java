package fr.hardel.leafs.mixin.entity;

import fr.hardel.leafs.entity.ServerLevelEntityAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Section crossings re-home the entity between the owning units' lists. The old section is captured
 * at the head because {@code onMove} overwrites it before the status callbacks run; the instance is
 * per entity, so the scratch field cannot be re-entered.
 */
@Mixin(targets = "net.minecraft.world.level.entity.PersistentEntitySectionManager$Callback")
public abstract class SectionCallbackMixin {

    @Shadow
    @Final
    private EntityAccess entity;

    @Shadow
    private long currentSectionKey;

    @Unique
    private long leafs$sectionBeforeMove;

    @Inject(method = "onMove", at = @At("HEAD"))
    private void leafs$captureSectionBeforeMove(CallbackInfo callbackInfo) {
        leafs$sectionBeforeMove = currentSectionKey;
    }

    /** Before {@code updateStatus}: a ticking-to-hidden crossing must find its entry already re-homed to remove it. */
    @Inject(method = "onMove", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/PersistentEntitySectionManager$Callback;updateStatus(Lnet/minecraft/world/level/entity/Visibility;Lnet/minecraft/world/level/entity/Visibility;)V"))
    private void leafs$reHomeAcrossUnits(CallbackInfo callbackInfo) {
        long oldSectionKey = leafs$sectionBeforeMove;
        if (oldSectionKey == currentSectionKey || !(entity instanceof Entity moved) || !(moved.level() instanceof ServerLevel level)) {
            return;
        }

        ((ServerLevelEntityAccess) level).leafs$entityLists().sectionMoved(moved, SectionPos.x(oldSectionKey), SectionPos.z(oldSectionKey));
    }
}
