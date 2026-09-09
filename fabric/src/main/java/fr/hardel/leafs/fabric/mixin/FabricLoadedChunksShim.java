package fr.hardel.leafs.fabric.mixin;

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

/** Swaps fabric-lifecycle-events' loaded-chunks HashSet for a concurrent set; priority 1100 for ordering. */
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
