package fr.hardel.leafs.chunk.disk;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StreamTagVisitor;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** A chunk between its save and its file write: vanilla's photo future, serving readers from the photo until the bytes exist. */
public final class PendingWrite extends CompletableFuture<CompoundTag> {
    private final CompletableFuture<Void> written = new CompletableFuture<>();
    private volatile Supplier<CompoundTag> photo;
    private volatile CompressedChunk bytes;

    public PendingWrite(Supplier<CompoundTag> photo) {
        this.photo = photo;
    }

    /** Done once the bytes are in the file. */
    public CompletableFuture<Void> written() {
        return written;
    }

    public CompressedChunk bytes() {
        return bytes;
    }

    /** The photo is dropped; a reader that took it first still holds it. */
    public void compressed(CompressedChunk compressed) {
        bytes = compressed;
        photo = null;
    }

    /** A fresh tag, like vanilla's copy of its pending tree. */
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
