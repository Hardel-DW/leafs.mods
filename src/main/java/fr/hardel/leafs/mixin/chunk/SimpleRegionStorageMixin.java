package fr.hardel.leafs.mixin.chunk;

import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Never DSYNC: sync-chunk-writes is ignored, like C2ME. */
@Mixin(SimpleRegionStorage.class)
public abstract class SimpleRegionStorageMixin {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/storage/IOWorker;<init>(Lnet/minecraft/world/level/chunk/storage/RegionStorageInfo;Ljava/nio/file/Path;Z)V"), index = 2)
    private boolean leafs$noSyncWrites(boolean syncWrites) {
        return false;
    }
}
