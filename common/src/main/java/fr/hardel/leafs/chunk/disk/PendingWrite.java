package fr.hardel.leafs.chunk.disk;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StreamTagVisitor;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class PendingWrite extends CompletableFuture<CompoundTag> {
    private final CompletableFuture<Void> written = new CompletableFuture<>();
    private volatile Supplier<CompoundTag> photo;
    private volatile CompressedChunk bytes;

    public PendingWrite(Supplier<CompoundTag> photo) {
        this.photo = photo;
    }

    public CompletableFuture<Void> written() {
        return written;
    }

    public CompressedChunk bytes() {
        return bytes;
    }

    public void compressed(CompressedChunk compressed) {
        bytes = compressed;
        photo = null;
    }

    public CompoundTag read() throws IOException {
        Supplier<CompoundTag> taken = photo;
        CompressedChunk ready = bytes;
        return ready != null ? ready.read() : taken.get();
    }

    public void scan(StreamTagVisitor visitor) throws IOException {
        Supplier<CompoundTag> taken = photo;
        CompressedChunk ready = bytes;
        if (ready != null) {
            ready.scan(visitor);
            return;
        }

        taken.get().acceptAsRoot(visitor);
    }
}
