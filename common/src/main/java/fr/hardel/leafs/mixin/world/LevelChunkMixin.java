package fr.hardel.leafs.mixin.world;

import fr.hardel.excess.ConcurrentInt2ObjectMap;
import fr.hardel.leafs.world.ChunkBlockEvents;
import fr.hardel.leafs.world.ChunkTickAccess;
import fr.hardel.leafs.world.ChunkTickers;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gameevent.GameEventListenerRegistry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The chunk carries its tickers and its block events; a thread that does not own it reads block entities without creating one. */
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin implements ChunkTickAccess {

    @Shadow
    @Final
    @Mutable
    private Int2ObjectMap<GameEventListenerRegistry> gameEventListenerRegistrySections;

    @Inject(method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/UpgradeData;"
        + "Lnet/minecraft/world/ticks/LevelChunkTicks;Lnet/minecraft/world/ticks/LevelChunkTicks;J[Lnet/minecraft/world/level/chunk/LevelChunkSection;"
        + "Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;Lnet/minecraft/world/level/levelgen/blending/BlendingData;)V", at = @At("TAIL"))
    private void leafs$concurrentListenerSections(CallbackInfo callbackInfo) {
        Int2ObjectMap<GameEventListenerRegistry> concurrent = new ConcurrentInt2ObjectMap<>();
        concurrent.putAll(this.gameEventListenerRegistrySections);
        this.gameEventListenerRegistrySections = concurrent;
    }

    /** A caller may still hold the empty registry and register into it; its identity lasts until the chunk is discarded. */
    @Inject(method = "removeGameEventListenerRegistry", at = @At("HEAD"), cancellable = true)
    private void leafs$keepListenerRegistry(CallbackInfo callbackInfo) {
        callbackInfo.cancel();
    }

    @Unique
    private final ChunkTickers leafs$tickers = new ChunkTickers();

    @Unique
    private final ChunkBlockEvents leafs$blockEvents = new ChunkBlockEvents();

    @Override
    public BlockEntity leafs$existingBlockEntity(BlockPos pos) {
        return ((LevelChunk) (Object) this).getBlockEntities().get(pos);
    }

    @Override
    public ChunkTickers leafs$tickers() {
        return leafs$tickers;
    }

    @Override
    public ChunkBlockEvents leafs$blockEvents() {
        return leafs$blockEvents;
    }
}
