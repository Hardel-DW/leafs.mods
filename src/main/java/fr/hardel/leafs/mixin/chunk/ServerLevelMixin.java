package fr.hardel.leafs.mixin.chunk;

import fr.hardel.leafs.ownership.RegionContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chunk promotion/demotion side effects mutate game state (tick containers, entity lists) but run on
 * the chunk thread since the executor re-point. They hop to the server event loop — same thread as
 * the game tick, pumped between ticks and during managed blocks, vanilla's own timing. M11 reroutes
 * to the owning region's task queue.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    /** A stopping event loop runs submissions inline on the caller — the guard breaks the resulting recursion. */
    @Unique
    private final ThreadLocal<Boolean> leafs$applying = ThreadLocal.withInitial(() -> false);

    @Inject(method = "startTickingChunk", at = @At("HEAD"), cancellable = true)
    private void leafs$startTickingOnOwner(LevelChunk chunk, CallbackInfo callbackInfo) {
        if (!leafs$applying.get() && RegionContext.current() instanceof RegionContext.Chunk) {
            ServerLevel self = (ServerLevel) (Object) this;
            self.getServer().execute(() -> leafs$applyDirect(() -> self.startTickingChunk(chunk)));
            callbackInfo.cancel();
        }
    }

    @Inject(method = "unload", at = @At("HEAD"), cancellable = true)
    private void leafs$unloadOnOwner(LevelChunk chunk, CallbackInfo callbackInfo) {
        if (!leafs$applying.get() && RegionContext.current() instanceof RegionContext.Chunk) {
            ServerLevel self = (ServerLevel) (Object) this;
            self.getServer().execute(() -> leafs$applyDirect(() -> self.unload(chunk)));
            callbackInfo.cancel();
        }
    }

    @Unique
    private void leafs$applyDirect(Runnable action) {
        leafs$applying.set(true);
        try {
            action.run();
        } finally {
            leafs$applying.set(false);
        }
    }
}
