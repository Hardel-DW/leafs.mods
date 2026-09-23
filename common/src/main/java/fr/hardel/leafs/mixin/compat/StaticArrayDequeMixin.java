package fr.hardel.leafs.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.SynchronizedArrayDeque;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayDeque;

@Pseudo
@Mixin(targets = {
    // Shadows-of-Fire/Placebo, task queue, fed by any mod from any thread and emptied by the server tick.
    "dev.shadowsoffire.placebo.util.PlaceboTaskQueue$Impl"
})
public abstract class StaticArrayDequeMixin {

    @WrapOperation(method = "<clinit>", at = @At(value = "NEW", target = "java/util/ArrayDeque"))
    private static <E> ArrayDeque<E> leafs$synchronized(Operation<ArrayDeque<E>> original) {
        return new SynchronizedArrayDeque<>();
    }
}
