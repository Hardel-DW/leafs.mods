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
 * Post-processing a promoted chunk ticks blocks (fluids, bubble columns) that reach the LEVEL-wide
 * neighbor updater — game state, so it hops off the chunk thread like its promotion siblings.
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {

    @Unique
    private final ThreadLocal<Boolean> leafs$applying = ThreadLocal.withInitial(() -> false);

    @Inject(method = "postProcessGeneration", at = @At("HEAD"), cancellable = true)
    private void leafs$postProcessOnOwner(ServerLevel level, CallbackInfo callbackInfo) {
        if (!leafs$applying.get() && RegionContext.current() instanceof RegionContext.Chunk) {
            LevelChunk self = (LevelChunk) (Object) this;
            level.getServer().execute(() -> {
                leafs$applying.set(true);
                try {
                    self.postProcessGeneration(level);
                } finally {
                    leafs$applying.set(false);
                }
            });
            callbackInfo.cancel();
        }
    }
}
