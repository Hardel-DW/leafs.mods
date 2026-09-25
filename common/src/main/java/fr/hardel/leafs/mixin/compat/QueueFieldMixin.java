package fr.hardel.leafs.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Pseudo
@Mixin(targets = {
    // Shadows-of-Fire/Placebo, task queue, fed by any mod from any thread and emptied by the server tick.
    "dev.shadowsoffire.placebo.util.PlaceboTaskQueue$Impl"
})
public abstract class QueueFieldMixin {

    @WrapOperation(method = "<clinit>", at = {
        @At(value = "FIELD", opcode = Opcodes.PUTSTATIC, target = "Ldev/shadowsoffire/placebo/util/PlaceboTaskQueue$Impl;TASKS:Ljava/util/Queue;")
    })
    private static <E> void leafs$concurrent(Queue<E> vanilla, Operation<Void> original) {
        original.call(new ConcurrentLinkedQueue<>(vanilla));
    }
}
