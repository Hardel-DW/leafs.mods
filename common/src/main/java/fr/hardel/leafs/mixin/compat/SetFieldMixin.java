package fr.hardel.leafs.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Pseudo
@Mixin(targets = {
    // klikli-dev/theurgy, logistics graph nodes, added and removed by leaf nodes from their region while other regions read them.
    "com.klikli_dev.theurgy.logistics.Logistics",
    // klikli-dev/theurgy, wires, added and removed from the placing region while other regions read them.
    "com.klikli_dev.theurgy.logistics.Wires"
})
public abstract class SetFieldMixin {

    @WrapOperation(method = "<init>*", at = {
        @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target = "Lcom/klikli_dev/theurgy/logistics/Logistics;graphNodes:Ljava/util/Set;"),
        @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target = "Lcom/klikli_dev/theurgy/logistics/Wires;wires:Ljava/util/Set;")
    })
    private static <E> void leafs$concurrentField(@Coerce Object owner, Set<E> vanilla, Operation<Void> original) {
        Set<E> concurrent = ConcurrentHashMap.newKeySet();
        concurrent.addAll(vanilla);
        original.call(owner, concurrent);
    }
}
