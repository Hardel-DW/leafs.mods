package fr.hardel.leafs.chunk.disk;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;

/** A chunk's bytes as the region file stores them: vanilla's five byte header, then the compressed NBT. */
public record CompressedChunk(byte[] bytes) {
    private static final int HEADER = 5;

    public static CompressedChunk of(CompoundTag tag) {
        RegionFileVersion version = RegionFileVersion.getSelected();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(8096);
        buffer.write(0);
        buffer.write(0);
        buffer.write(0);
        buffer.write(0);
        buffer.write(version.getId());
        try (DataOutputStream output = new DataOutputStream(version.wrap(buffer))) {
            NbtIo.write(tag, output);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }

        byte[] bytes = buffer.toByteArray();
        ByteBuffer.wrap(bytes).putInt(0, bytes.length - HEADER + 1);
        return new CompressedChunk(bytes);
    }

    public ByteBuffer buffer() {
        return ByteBuffer.wrap(bytes);
    }

    public int streamLength() {
        return bytes.length - HEADER + 1;
    }

    public RegionFileVersion version() {
        return RegionFileVersion.fromId(bytes[HEADER - 1]);
    }

    public CompoundTag read() throws IOException {
        try (DataInputStream input = stream()) {
            return NbtIo.read(input);
        }
    }

    public void scan(StreamTagVisitor visitor) throws IOException {
        try (DataInputStream input = stream()) {
            NbtIo.parse(input, visitor, NbtAccounter.unlimitedHeap());
        }
    }

    private DataInputStream stream() throws IOException {
        return new DataInputStream(version().wrap(new ByteArrayInputStream(bytes, HEADER, bytes.length - HEADER)));
    }
}
