package fr.hardel.leafs.mixin.chunk;

import fr.hardel.leafs.chunk.DistanceManagerAccess;
import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.level.ChunkLevels;
import fr.hardel.leafs.chunk.view.PlayerView;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.TriState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The three graphs answer every question vanilla asked its four trackers; the trackers run empty. */
@Mixin(DistanceManager.class)
public abstract class DistanceManagerMixin implements DistanceManagerAccess {
    @Unique
    private LevelChunks leafs$chunks;

    @Override
    public void leafs$bind(LevelChunks chunks) {
        leafs$chunks = chunks;
    }

    @Inject(method = "addPlayer", at = @At("HEAD"), cancellable = true)
    private void leafs$playerEnters(SectionPos pos, ServerPlayer player, CallbackInfo callbackInfo) {
        leafs$view().enter(pos.chunk().pack());
        callbackInfo.cancel();
    }

    @Inject(method = "removePlayer", at = @At("HEAD"), cancellable = true)
    private void leafs$playerLeaves(SectionPos pos, ServerPlayer player, CallbackInfo callbackInfo) {
        leafs$view().leave(pos.chunk().pack());
        callbackInfo.cancel();
    }

    /** The server thread's drain; everything else drained already. */
    @Inject(method = "runAllUpdates", at = @At("HEAD"), cancellable = true)
    private void leafs$drainTheGraphs(ChunkMap scheduler, CallbackInfoReturnable<Boolean> callbackInfo) {
        callbackInfo.setReturnValue(leafs$chunks.graphs().drain());
    }

    @Inject(method = "updatePlayerTickets", at = @At("HEAD"), cancellable = true)
    private void leafs$viewDistance(int viewDistance, CallbackInfo callbackInfo) {
        leafs$view().viewDistance(viewDistance);
        callbackInfo.cancel();
    }

    @Inject(method = "updateSimulationDistance", at = @At("HEAD"), cancellable = true)
    private void leafs$simulationDistance(int newDistance, CallbackInfo callbackInfo) {
        leafs$view().simulationDistance(newDistance);
        callbackInfo.cancel();
    }

    @Inject(method = "inEntityTickingRange", at = @At("HEAD"), cancellable = true)
    private void leafs$entityTickingFromTheGraph(long key, CallbackInfoReturnable<Boolean> callbackInfo) {
        callbackInfo.setReturnValue(ChunkLevel.isEntityTicking(leafs$simulation().level(key)));
    }

    @Inject(method = "inBlockTickingRange", at = @At("HEAD"), cancellable = true)
    private void leafs$blockTickingFromTheGraph(long key, CallbackInfoReturnable<Boolean> callbackInfo) {
        callbackInfo.setReturnValue(ChunkLevel.isBlockTicking(leafs$simulation().level(key)));
    }

    @Inject(method = "getChunkLevel", at = @At("HEAD"), cancellable = true)
    private void leafs$levelFromTheGraph(long key, boolean simulation, CallbackInfoReturnable<Integer> callbackInfo) {
        callbackInfo.setReturnValue(simulation ? leafs$simulation().level(key) : leafs$chunks.graphs().loading().level(key));
    }

    @Inject(method = "forEachEntityTickingChunk", at = @At("HEAD"), cancellable = true)
    private void leafs$entityTickingChunksFromTheGraph(LongConsumer consumer, CallbackInfo callbackInfo) {
        leafs$simulation().forEachAtMost(ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING), consumer);
        callbackInfo.cancel();
    }

    @Inject(method = "hasPlayersNearby", at = @At("HEAD"), cancellable = true)
    private void leafs$spawnProximity(long pos, CallbackInfoReturnable<TriState> callbackInfo) {
        callbackInfo.setReturnValue(leafs$view().nearby(pos));
    }

    @Inject(method = "getNaturalSpawnChunkCount", at = @At("HEAD"), cancellable = true)
    private void leafs$spawnChunkCount(CallbackInfoReturnable<Integer> callbackInfo) {
        callbackInfo.setReturnValue(leafs$view().spawnChunkCount());
    }

    @Inject(method = "getSpawnCandidateChunks", at = @At("HEAD"), cancellable = true)
    private void leafs$spawnCandidates(CallbackInfoReturnable<LongIterator> callbackInfo) {
        callbackInfo.setReturnValue(leafs$view().spawnCandidates());
    }

    @Unique
    private PlayerView leafs$view() {
        return leafs$chunks.view();
    }

    @Unique
    private ChunkLevels leafs$simulation() {
        return leafs$chunks.graphs().simulation();
    }
}
