package fr.hardel.leafs.mixin.compat.placebo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.SynchronizedArrayDeque;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayDeque;

/** Shadows-of-Fire/Placebo, task queue. Any mod submits from any thread; the server tick walks the queue and removes the completed tasks. */
@Pseudo
@Mixin(targets = "dev.shadowsoffire.placebo.util.PlaceboTaskQueue$Impl")
public abstract class PlaceboTaskQueueMixin {

    @WrapOperation(method = "<clinit>", at = @At(value = "NEW", target = "java/util/ArrayDeque"))
    private static <E> ArrayDeque<E> leafs$sharedTasks(Operation<ArrayDeque<E>> original) {
        return new SynchronizedArrayDeque<>();
    }
}
