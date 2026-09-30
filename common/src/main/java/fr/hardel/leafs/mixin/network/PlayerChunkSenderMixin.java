package fr.hardel.leafs.mixin.network;

import fr.hardel.excess.ConcurrentLongSet;
import fr.hardel.leafs.network.AwaitedChunks;
import fr.hardel.leafs.network.AwaitedChunksAccess;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerChunkSender.class)
public abstract class PlayerChunkSenderMixin implements AwaitedChunksAccess {
    @Unique
    private final AwaitedChunks leafs$awaited = new AwaitedChunks();

    @Mutable
    @Shadow
    @Final
    private LongSet pendingChunks;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$concurrentPendingSet(boolean memoryConnection, CallbackInfo callbackInfo) {
        this.pendingChunks = new ConcurrentLongSet();
    }

    @Override
    public AwaitedChunks leafs$awaited() {
        return leafs$awaited;
    }

    @Inject(method = "dropChunk", at = @At("HEAD"))
    private void leafs$forgetTheAwaitedChunk(ServerPlayer player, ChunkPos pos, CallbackInfo callbackInfo) {
        leafs$awaited.take(pos.pack());
    }
}
