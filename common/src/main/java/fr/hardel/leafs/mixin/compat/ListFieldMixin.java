package fr.hardel.leafs.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.RemovableCopyOnWriteList;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Pseudo
@Mixin(targets = {
    // Direwolf20-MC/LaserIO, particle lists, filled by cards from their region and sent and cleared by the server tick.
    "com.direwolf20.laserio.common.events.ServerTickHandler",
    // Direwolf20-MC/MiningGadgets, durability sync list, filled by mining from the player's region and sent and cleared by the server tick.
    "com.direwolf20.mininggadgets.common.events.ServerTickHandler",
    // JDKDigital/utilitarian, leaf decay queue, filled by onLogBreak from the breaking player's region and walked and emptied by the server tick.
    "cy.jdkdigital.utilitarian.event.ModEventHandler"
})
public abstract class ListFieldMixin {

    @WrapOperation(method = "<clinit>", at = {
        @At(value = "FIELD", opcode = Opcodes.PUTSTATIC, target = "Lcom/direwolf20/laserio/common/events/ServerTickHandler;particleList:Ljava/util/List;"),
        @At(value = "FIELD", opcode = Opcodes.PUTSTATIC, target = "Lcom/direwolf20/laserio/common/events/ServerTickHandler;particleListFluid:Ljava/util/List;"),
        @At(value = "FIELD", opcode = Opcodes.PUTSTATIC, target = "Lcom/direwolf20/mininggadgets/common/events/ServerTickHandler;updateList:Ljava/util/List;")
    })
    private static <E> void leafs$copyOnWrite(List<E> vanilla, Operation<Void> original) {
        original.call(new CopyOnWriteArrayList<>(vanilla));
    }

    @WrapOperation(method = "<clinit>", at = {
        @At(value = "FIELD", opcode = Opcodes.PUTSTATIC, target = "Lcy/jdkdigital/utilitarian/event/ModEventHandler;DECAY_QUEUE:Ljava/util/List;")
    })
    private static <E> void leafs$removableCopyOnWrite(List<E> vanilla, Operation<Void> original) {
        RemovableCopyOnWriteList<E> concurrent = new RemovableCopyOnWriteList<>();
        concurrent.addAll(vanilla);
        original.call(concurrent);
    }
}
