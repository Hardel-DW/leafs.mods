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
    // squeek502/AppleSkin, per player levels, written by every player tick from the player's region.
    "squeek.appleskin.network.SyncHandler",
    // benbenlaw/appliedsticks, per player positions, written by every player tick from the player's region.
    "com.benbenlaw.appliedsticks.event.ServerEvents",
    // BreakinBlocks/Auroral, per player positions, written by dimension changes from the player's region.
    "com.breakinblocks.auroral.events.PlayerEventHandler",
    // Direwolf20-MC/BuildingGadgets2, build queue, filled by gadgets from the player's region and emptied by the server tick.
    "com.direwolf20.buildinggadgets2.common.events.ServerTickHandler",
    // Direwolf20-MC/JustDireThings, fluid craft cache, filled by item entities from their region.
    "com.direwolf20.justdirethings.common.events.EntityEvents",
    // LootrMinecraft/Lootr#895, section saved data types, filled by getUpdateTag from every chunk owner.
    "noobanidus.mods.lootr.common.data.DataStorage",
    // stepsword/mahoutsukai, staff users, written by every staff use from the player's region.
    "stepsword.mahoutsukai.item.emrys.StaffEmrys",
    // stepsword/mahoutsukai, tag subset cache, filled by isSubset from the casting player's region.
    "stepsword.mahoutsukai.effects.projection.StrengtheningSpellEffect",
    // stepsword/mahoutsukai, familiars, written by summons and familiar ticks from their region.
    "stepsword.mahoutsukai.effects.familiar.SummonFamiliarSpellEffect",
    // stepsword/mahoutsukai, lecterns, written by a right click from the player's region while every player tick walks it.
    "stepsword.mahoutsukai.item.william.William",
    // Commoble/morered, shape cache, filled by getShape from whichever thread asks for a shape.
    "net.commoble.morered.mechanisms.GearsBlock",
    // BreakinBlocks/NeoVitae, per player maps, written by player ticks and falls from the player's region.
    "com.breakinblocks.neovitae.common.event.CommonEventHandler",
    // JDKDigital/productivebees, recipe cache, filled by nest ticks from their region.
    "cy.jdkdigital.productivebees.common.block.SolitaryNest",
    // benbenlaw/refinedsticks, per player positions, written by every player tick from the player's region.
    "com.benbenlaw.refinedsticks.event.ServerEvents",
    // P3pp3rF1y/Reliquary, per player flight status, written by every player tick from the player's region.
    "reliquary.handler.CommonEventHandler",
    // P3pp3rF1y/SophisticatedCore, fake players, asked by upgrades from their region.
    "net.p3pp3rf1y.sophisticatedcore.util.CoreFakePlayer",
    // JDKDigital/utilitarian, visited leaves, filled by onLogBreak from the breaking player's region and cleared by the server tick.
    "cy.jdkdigital.utilitarian.event.ModEventHandler"
})
public abstract class StaticHashMapMixin {

    @WrapOperation(method = "<clinit>", at = @At(value = "NEW", target = "java/util/HashMap"))
    private static <K, V> HashMap<K, V> leafs$synchronized(Operation<HashMap<K, V>> original) {
        return new SynchronizedHashMap<>();
    }
}
