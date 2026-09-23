package fr.hardel.leafs.mixin.compat;

import com.google.common.collect.MapMaker;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Pseudo
@Mixin(targets = {
    // squeek502/AppleSkin, per player levels, written by every player tick from the player's region.
    "squeek.appleskin.network.SyncHandler",
    // BreakinBlocks/Auroral, per player positions, written by dimension changes from the player's region.
    "com.breakinblocks.auroral.events.PlayerEventHandler",
    // Direwolf20-MC/JustDireThings, fluid craft cache, filled by item entities from their region.
    "com.direwolf20.justdirethings.common.events.EntityEvents",
    // LootrMinecraft/Lootr#895, section saved data types, filled by getUpdateTag from every chunk owner.
    "noobanidus.mods.lootr.common.data.DataStorage",
    // LootrMinecraft/Lootr#895, container stores, inserted by getStore from every chunk owner.
    "noobanidus.mods.lootr.common.data.Section",
    // stepsword/mahoutsukai, tag subset cache, filled by isSubset from the casting player's region.
    "stepsword.mahoutsukai.effects.projection.StrengtheningSpellEffect",
    // Commoble/morered, shape caches, filled by getShape from whichever thread asks for a shape.
    "net.commoble.morered.mechanisms.GearsBlock",
    // BreakinBlocks/NeoVitae, per player maps, written by player ticks and falls from the player's region.
    "com.breakinblocks.neovitae.common.event.CommonEventHandler",
    // JDKDigital/productivebees, recipe cache, filled by nest ticks from their region.
    "cy.jdkdigital.productivebees.common.block.SolitaryNest",
    // P3pp3rF1y/Reliquary, per player flight status, written by every player tick from the player's region.
    "reliquary.handler.CommonEventHandler",
    // P3pp3rF1y/SophisticatedCore, fake players, asked by upgrades from their region.
    "net.p3pp3rf1y.sophisticatedcore.util.CoreFakePlayer",
    // P3pp3rF1y/SophisticatedBackpacks, templates, written by commands from the server thread while items read them from their region.
    "net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackTemplateStorage",
    // JDKDigital/utilitarian, visited positions per dimension, filled by onLogBreak from the breaking player's region and cleared by the server tick.
    "cy.jdkdigital.utilitarian.event.ModEventHandler",
    // XFactHD/FramedBlocks, culling updates, enqueued by framed blocks from their region and walked and cleared per dimension by the level tick.
    "io.github.xfacthd.framedblocks.common.data.cullupdate.CullingUpdateTracker",
    // Shadows-of-Fire/FastWorkbench, slot updates, queued by crafting from the player's region and run and cleared by the server tick.
    "dev.shadowsoffire.fastbench.util.SlotUpdateManager",
    // klikli-dev/theurgy, logistics networks, written by leaf nodes from their region while other regions read them.
    "com.klikli_dev.theurgy.logistics.Logistics",
    // Commoble/exmachina, mechanical buffer, fed by enqueue on every NeighborNotifyEvent from any thread while tick swaps and walks it.
    "net.commoble.exmachina.internal.mechanical.MechanicalGraphBuffer",
    // Commoble/exmachina, signal buffer, fed by enqueue on every VanillaGameEvent from any thread while tick swaps and walks it.
    "net.commoble.exmachina.internal.signal.SignalGraphBuffer",
    // Team-EnderIO/EnderIO, hang glider falling ticks, written by every player tick from the player's region.
    "com.enderio.enderio.content.tools.hang_glider.PlayerMovementHandler"
})
public abstract class MapFieldMixin {

    @WrapOperation(method = "<clinit>", at = {
        @At(value = "FIELD", opcode = Opcodes.PUTSTATIC, target = "Lsqueek/appleskin/network/SyncHandler;lastSaturationLevels:Ljava/util/Map;"),
        @At(value = "FIELD", opcode = Opcodes.PUTSTATIC, target = "Lsqueek/appleskin/network/SyncHandler;lastExhaustionLevels:Ljava/util/Map;"),
        @At(value = "FIELD", opcode = Opcodes.PUTSTATIC, target = "Lcom/breakinblocks/auroral/events/PlayerEventHandler;preTransitPositions:Ljava/util/Map;"),
        @At(value = "FIELD", opcode = Opcodes.PUTSTATIC, target = "Lcom/direwolf20/justdirethings/common/events/EntityEvents;fluidCraftCache:Ljava/util/Map;"),
        @At(value = "FIELD", opcode = Opcodes.PUTSTATIC, target = "Lnoobanidus/mods/lootr/common/data/DataStorage;SECTION_SAVED_DATA:Ljava/util/Map;"),
        @At(value = "FIELD", opcode = Opcodes.PUTSTATIC, target = "Lstepsword/mahoutsukai/effects/projection/StrengtheningSpellEffect;CACHE:Ljava/util/Map;"),
        @At(value = "FIELD", opcode = Opcodes.PUTSTATIC, target = "Lnet/commoble/morered/mechanisms/GearsBlock;SHAPE_CACHE:Ljava/util/Map;"),
        @At(value = "FIELD", opcode = Opcodes.PUTSTATIC, target = "Lnet/commoble/morered/mechanisms/GearsBlock;SHAPES_BY_BITFLAG:Ljava/util/Map;"),
        @At(value = "FIELD", opcode = Opcodes.PUTSTATIC, target = "Lcom/breakinblocks/neovitae/common/event/CommonEventHandler;bounceMap:Ljava/util/Map;"),
        @At(value = "FIELD", opcode = Opcodes.PUTSTATIC, target = "Lcom/breakinblocks/neovitae/common/event/CommonEventHandler;dungeonGracePeriod:Ljava/util/Map;"),
        @At(value = "FIELD", opcode = Opcodes.PUTSTATIC, target = "Lcom/breakinblocks/neovitae/common/event/CommonEventHandler;lastSyncedAnima:Ljava/util/Map;"),
        @At(value = "FIELD", opcode = Opcodes.PUTSTATIC, target = "Lcy/jdkdigital/productivebees/common/block/SolitaryNest;recipes:Ljava/util/Map;"),
        @At(value = "FIELD", opcode = Opcodes.PUTSTATIC, target = "Lreliquary/handler/CommonEventHandler;playersFlightStatus:Ljava/util/Map;"),
        @At(value = "FIELD", opcode = Opcodes.PUTSTATIC, target = "Lnet/p3pp3rf1y/sophisticatedcore/util/CoreFakePlayer;fakePlayers:Ljava/util/Map;"),
        @At(value = "FIELD", opcode = Opcodes.PUTSTATIC, target = "Lcy/jdkdigital/utilitarian/event/ModEventHandler;VISITED_THIS_TICK:Ljava/util/Map;"),
        @At(value = "FIELD", opcode = Opcodes.PUTSTATIC, target = "Lio/github/xfacthd/framedblocks/common/data/cullupdate/CullingUpdateTracker;UPDATED_POSITIONS:Ljava/util/Map;"),
        @At(value = "FIELD", opcode = Opcodes.PUTSTATIC, target = "Ldev/shadowsoffire/fastbench/util/SlotUpdateManager;UPDATES:Ljava/util/Map;")
    })
    private static <K, V> void leafs$concurrentStatic(Map<K, V> vanilla, Operation<Void> original) {
        original.call(new ConcurrentHashMap<>(vanilla));
    }

    @WrapOperation(method = {"<init>*", "tick"}, at = {
        @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target = "Lnoobanidus/mods/lootr/common/data/Section;data:Ljava/util/Map;"),
        @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target = "Lnet/p3pp3rf1y/sophisticatedbackpacks/backpack/BackpackTemplateStorage;backpackTemplates:Ljava/util/Map;"),
        @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target = "Lcom/klikli_dev/theurgy/logistics/Logistics;blockPosToNetwork:Ljava/util/Map;"),
        @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target = "Lcom/klikli_dev/theurgy/logistics/Logistics;cachedLeafNodes:Ljava/util/Map;"),
        @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target = "Lnet/commoble/exmachina/internal/mechanical/MechanicalGraphBuffer;positions:Ljava/util/Map;"),
        @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target = "Lnet/commoble/exmachina/internal/signal/SignalGraphBuffer;positions:Ljava/util/Map;")
    })
    private static <K, V> void leafs$concurrentField(@Coerce Object owner, Map<K, V> vanilla, Operation<Void> original) {
        original.call(owner, new ConcurrentHashMap<>(vanilla));
    }

    @WrapOperation(method = "<clinit>", at = {
        @At(value = "FIELD", opcode = Opcodes.PUTSTATIC, target = "Lcom/enderio/enderio/content/tools/hang_glider/PlayerMovementHandler;TICKS_FALLING_CLIENT:Ljava/util/Map;"),
        @At(value = "FIELD", opcode = Opcodes.PUTSTATIC, target = "Lcom/enderio/enderio/content/tools/hang_glider/PlayerMovementHandler;TICKS_FALLING_SERVER:Ljava/util/Map;")
    })
    private static <K, V> void leafs$concurrentWeak(Map<K, V> vanilla, Operation<Void> original) {
        ConcurrentMap<K, V> weak = new MapMaker().weakKeys().makeMap();
        weak.putAll(vanilla);
        original.call(weak);
    }
}
