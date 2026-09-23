package fr.hardel.leafs.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.SynchronizedHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashMap;

@Pseudo
@Mixin(targets = {
    // benbenlaw/appliedsticks, per player positions, written by every player tick from the player's region.
    "com.benbenlaw.appliedsticks.event.ServerEvents",
    // Direwolf20-MC/BuildingGadgets2, build queue, filled by gadgets from the player's region and emptied by the server tick.
    "com.direwolf20.buildinggadgets2.common.events.ServerTickHandler",
    // stepsword/mahoutsukai, staff users, written by every staff use from the player's region.
    "stepsword.mahoutsukai.item.emrys.StaffEmrys",
    // stepsword/mahoutsukai, familiars, written by summons and familiar ticks from their region.
    "stepsword.mahoutsukai.effects.familiar.SummonFamiliarSpellEffect",
    // stepsword/mahoutsukai, lecterns, written by a right click from the player's region while every player tick walks it.
    "stepsword.mahoutsukai.item.william.William",
    // benbenlaw/refinedsticks, per player positions, written by every player tick from the player's region.
    "com.benbenlaw.refinedsticks.event.ServerEvents"
})
public abstract class StaticHashMapMixin {

    @WrapOperation(method = "<clinit>", at = @At(value = "NEW", target = "java/util/HashMap"))
    private static <K, V> HashMap<K, V> leafs$synchronized(Operation<HashMap<K, V>> original) {
        return new SynchronizedHashMap<>();
    }
}
