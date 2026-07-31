package fr.hardel.leafs.mixin.compat;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * fabric-lifecycle-events tracks loaded chunks in a plain HashSet, written by chunk promotion —
 * which runs on our chunk thread — and iterated by the server thread. Swapped for a concurrent set;
 * priority 1100 so this applies after fabric's mixin created the field.
 */
@Mixin(value = Level.class, priority = 1100)
public abstract class FabricLoadedChunksShim {

    @Mutable
    @Shadow(remap = false)
    private Set<LevelChunk> loadedChunks;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$concurrentLoadedChunks(CallbackInfo callbackInfo) {
        this.loadedChunks = ConcurrentHashMap.newKeySet();
    }
}
