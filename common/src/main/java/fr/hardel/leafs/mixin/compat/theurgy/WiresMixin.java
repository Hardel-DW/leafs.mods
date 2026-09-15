package fr.hardel.leafs.mixin.compat.theurgy;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.SynchronizedObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** klikli-dev/theurgy, wires. Wires are added and removed from the placing region while other regions read them. */
@Pseudo
@Mixin(targets = "com.klikli_dev.theurgy.logistics.Wires")
public abstract class WiresMixin {

    @WrapOperation(method = "<init>", at = @At(value = "NEW", target = "it/unimi/dsi/fastutil/objects/ObjectOpenHashSet"))
    private static <E> ObjectOpenHashSet<E> leafs$sharedWires(Operation<ObjectOpenHashSet<E>> original) {
        return new SynchronizedObjectOpenHashSet<>();
    }
}
