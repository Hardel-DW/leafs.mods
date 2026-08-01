package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chunk promotion completions mark chunks pending from the chunk thread while the server thread
 * drains the set in tickChildren. Synchronized set, and the drain holds the lock across its iteration.
 */
@Mixin(PlayerChunkSender.class)
public abstract class PlayerChunkSenderMixin {

    @Mutable
    @Shadow
    @Final
    private LongSet pendingChunks;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$synchronizedPendingChunks(CallbackInfo callbackInfo) {
        this.pendingChunks = LongSets.synchronize(new LongOpenHashSet());
    }

    @WrapMethod(method = "sendNextChunks")
    private void leafs$lockedDrain(ServerPlayer player, Operation<Void> original) {
        synchronized (pendingChunks) {
            original.call(player);
        }
    }
}
