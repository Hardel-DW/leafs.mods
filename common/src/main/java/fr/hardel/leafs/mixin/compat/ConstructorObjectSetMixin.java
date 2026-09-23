package fr.hardel.leafs.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.SynchronizedObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = {
    // klikli-dev/theurgy, logistics graph nodes, added and removed by leaf nodes from their region while other regions read them.
    "com.klikli_dev.theurgy.logistics.Logistics",
    // klikli-dev/theurgy, wires, added and removed from the placing region while other regions read them.
    "com.klikli_dev.theurgy.logistics.Wires"
})
public abstract class ConstructorObjectSetMixin {

    @WrapOperation(method = "<init>", at = @At(value = "NEW", target = "it/unimi/dsi/fastutil/objects/ObjectOpenHashSet"))
    private static <E> ObjectOpenHashSet<E> leafs$synchronized(Operation<ObjectOpenHashSet<E>> original) {
        return new SynchronizedObjectOpenHashSet<>();
    }
}
