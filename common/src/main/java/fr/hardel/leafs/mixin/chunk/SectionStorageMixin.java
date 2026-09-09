package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.chunk.SectionStorageAccess;
import fr.hardel.leafs.chunk.SectionStorageLock;
import fr.hardel.excess.ConcurrentLong2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/** Concurrent storage facade, and the storage's lock: its writes, its reads from disk, its saves and the graphs of its subclass take it. */
@Mixin(SectionStorage.class)
public abstract class SectionStorageMixin<R, P> implements SectionStorageAccess {

    @Mutable
    @Shadow
    @Final
    private Long2ObjectMap<Optional<R>> storage;

    @Unique
    private final SectionStorageLock leafs$lock = new SectionStorageLock();

    @Override
    public SectionStorageLock leafs$lock() {
        return leafs$lock;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$swapForConcurrentFacade(CallbackInfo callbackInfo) {
        this.storage = new ConcurrentLong2ObjectMap<>();
    }

    @WrapMethod(method = "unpackChunk(Lnet/minecraft/world/level/ChunkPos;)V")
    private void leafs$readFromDiskUnderTheLock(ChunkPos chunkPos, Operation<Void> original) {
        leafs$lock.runLocked(() -> original.call(chunkPos));
    }

    @WrapMethod(method = "flushAll")

    private void leafs$flushUnderTheLock(Operation<Void> original) {
        leafs$lock.runLocked(original::call);
    }

    /** A chunk save packs the sections the writes touch: same lock. */
    @WrapMethod(method = "flush")
    private void leafs$chunkFlushUnderTheLock(ChunkPos chunkPos, Operation<Void> original) {
        leafs$lock.runLocked(() -> original.call(chunkPos));
    }
}
