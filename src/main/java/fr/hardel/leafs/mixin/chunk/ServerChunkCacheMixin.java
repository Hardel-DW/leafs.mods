package fr.hardel.leafs.mixin.chunk;

import fr.hardel.leafs.chunk.RegionChunkAccess;
import fr.hardel.leafs.ticking.LevelOwnership;
import fr.hardel.leafs.ticking.ServerLevelRegionAccess;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Region workers answer thread-identity checks through ownership: mid-tick is a game thread for its level. */
@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheMixin {

    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    @Final
    private Thread mainThread;

    @Inject(method = "getChunkNow(II)Lnet/minecraft/world/level/chunk/LevelChunk;", at = @At("HEAD"), cancellable = true)
    private void leafs$regionReadPath(int x, int z, CallbackInfoReturnable<LevelChunk> callbackInfo) {
        if (leafs$regionWorkerHoldsLevel()) {
            ServerChunkCache self = (ServerChunkCache) (Object) this;
            callbackInfo.setReturnValue(RegionChunkAccess.fullChunkOrNull(self.chunkMap, x, z));
        }
    }

    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;", at = @At("HEAD"), cancellable = true)
    private void leafs$regionGetChunkPath(int x, int z, ChunkStatus targetStatus, boolean loadOrGenerate, CallbackInfoReturnable<ChunkAccess> callbackInfo) {
        if (leafs$regionWorkerHoldsLevel()) {
            ServerChunkCache self = (ServerChunkCache) (Object) this;
            callbackInfo.setReturnValue(RegionChunkAccess.presentChunkOrThrow(self.chunkMap, x, z, targetStatus, loadOrGenerate));
        }
    }

    /** Vanilla answers from the ticket level; the read path answers from presence. Both must agree or a correct hasChunk-then-read sequence crashes. */
    @Inject(method = "hasChunk(II)Z", at = @At("HEAD"), cancellable = true)
    private void leafs$regionHasChunkPath(int x, int z, CallbackInfoReturnable<Boolean> callbackInfo) {
        if (leafs$regionWorkerHoldsLevel()) {
            ServerChunkCache self = (ServerChunkCache) (Object) this;
            callbackInfo.setReturnValue(RegionChunkAccess.fullChunkOrNull(self.chunkMap, x, z) != null);
        }
    }

    @Unique
    private boolean leafs$regionWorkerHoldsLevel() {
        if (Thread.currentThread() == this.mainThread) {
            return false;
        }

        LevelOwnership ownership = ((ServerLevelRegionAccess) this.level).leafs$regions().ownership();

        return ownership.isRegionTickHeldByCurrentThread();
    }
}
