package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.chunk.ChunkWritesAccess;
import fr.hardel.leafs.chunk.disk.ChunkWrites;
import fr.hardel.leafs.chunk.disk.PendingWrite;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** The disk thread of a chunk storage serves the writes still on their way before the file, as vanilla serves its pending tree. */
@Mixin(IOWorker.class)
public abstract class IOWorkerMixin implements ChunkWritesAccess {
    @Unique
    private ChunkWrites leafs$writes;

    @Override
    public void leafs$bind(ChunkWrites writes) {
        leafs$writes = writes;
    }

    @WrapMethod(method = "loadAsync")
    private CompletableFuture<Optional<CompoundTag>> leafs$loadWhatIsOnItsWay(ChunkPos pos, Operation<CompletableFuture<Optional<CompoundTag>>> original) {
        PendingWrite pending = leafs$pending(pos);
        return pending == null ? original.call(pos) : leafs$self().submitThrowingTask(() -> Optional.of(pending.read()));
    }

    @WrapMethod(method = "scanChunk")
    private CompletableFuture<Void> leafs$scanWhatIsOnItsWay(ChunkPos pos, StreamTagVisitor visitor, Operation<CompletableFuture<Void>> original) {
        PendingWrite pending = leafs$pending(pos);
        return pending == null ? original.call(pos, visitor) : leafs$self().submitThrowingTask(() -> {
            pending.scan(visitor);
            return null;
        });
    }

    @WrapMethod(method = "synchronize")
    private CompletableFuture<Void> leafs$flushWhatIsOnItsWay(boolean flush, Operation<CompletableFuture<Void>> original) {
        return leafs$writes == null ? original.call(flush) : leafs$writes.settled().thenCompose(_ -> original.call(flush));
    }

    @Unique
    private @Nullable PendingWrite leafs$pending(ChunkPos pos) {
        return leafs$writes == null ? null : leafs$writes.pending(pos);
    }

    @Unique
    private IOWorker leafs$self() {
        return (IOWorker) (Object) this;
    }
}
