package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.chunk.ViewAdmissionAccess;
import net.minecraft.server.level.ThrottlingChunkTaskDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vanilla admits at most 4 view chunks in execution for the whole server, sized for a handful of
 * players. The cap becomes dynamic, one admission per connected player, so 500 players no longer
 * queue behind a gate of 4.
 */
@Mixin(ThrottlingChunkTaskDispatcher.class)
public abstract class ThrottlingChunkTaskDispatcherMixin implements ViewAdmissionAccess {

    @Unique
    private volatile int leafs$admissionCap;

    @Override
    public void leafs$admissionCap(int cap) {
        this.leafs$admissionCap = cap;
    }

    @WrapOperation(method = "popTasks", at = @At(value = "FIELD", target = "Lnet/minecraft/server/level/ThrottlingChunkTaskDispatcher;maxChunksInExecution:I"))
    private int leafs$dynamicCap(ThrottlingChunkTaskDispatcher dispatcher, Operation<Integer> original) {
        int cap = leafs$admissionCap;
        return Math.max(cap, original.call(dispatcher));
    }
}
