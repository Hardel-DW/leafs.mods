package fr.hardel.leafs.mixin.chunk;

import fr.hardel.leafs.chunk.TicketStorageAccess;
import fr.hardel.leafs.ownership.RegionContext;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.TicketStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.Executor;

/**
 * Ticket mutations from game threads hop to the chunk thread (optimistic true, Folia model) —
 * TicketStorage is single-owner state. The chunk thread and the pre-bind boot path run inline.
 */
@Mixin(TicketStorage.class)
public abstract class TicketStorageMixin implements TicketStorageAccess {

    @Unique
    private Executor leafs$chunkExecutor;

    @Override
    public void leafs$bindChunkExecutor(Executor executor) {
        this.leafs$chunkExecutor = executor;
    }

    @Inject(method = "addTicket(JLnet/minecraft/server/level/Ticket;)Z", at = @At("HEAD"), cancellable = true)
    private void leafs$hopAdd(long chunkKey, Ticket ticket, CallbackInfoReturnable<Boolean> callbackInfo) {
        if (leafs$mustHop()) {
            leafs$chunkExecutor.execute(() -> ((TicketStorage) (Object) this).addTicket(chunkKey, ticket));
            callbackInfo.setReturnValue(true);
        }
    }

    @Inject(method = "removeTicket(JLnet/minecraft/server/level/Ticket;)Z", at = @At("HEAD"), cancellable = true)
    private void leafs$hopRemove(long chunkKey, Ticket ticket, CallbackInfoReturnable<Boolean> callbackInfo) {
        if (leafs$mustHop()) {
            leafs$chunkExecutor.execute(() -> ((TicketStorage) (Object) this).removeTicket(chunkKey, ticket));
            callbackInfo.setReturnValue(true);
        }
    }

    @Unique
    private boolean leafs$mustHop() {
        return leafs$chunkExecutor != null && !(RegionContext.current() instanceof RegionContext.Chunk);
    }
}
