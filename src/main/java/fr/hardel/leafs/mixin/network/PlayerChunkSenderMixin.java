package fr.hardel.leafs.mixin.network;

import fr.hardel.excess.ConcurrentLongSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.network.PlayerChunkSender;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The pending set takes writers from the marking thread and the sending region; the scalar state stays owner only. */
@Mixin(PlayerChunkSender.class)
public abstract class PlayerChunkSenderMixin {

    @Mutable
    @Shadow
    @Final
    private LongSet pendingChunks;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$concurrentPendingSet(boolean memoryConnection, CallbackInfo callbackInfo) {
        this.pendingChunks = new ConcurrentLongSet();
    }
}
