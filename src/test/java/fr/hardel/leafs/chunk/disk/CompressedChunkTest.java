package fr.hardel.leafs.chunk.disk;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.visitors.CollectFields;
import net.minecraft.nbt.visitors.FieldSelector;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The bytes the pool produces are what vanilla's ChunkBuffer would have produced: the region file takes them and vanilla reads them back. */
class CompressedChunkTest {
    private static CompoundTag chunkTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", 4882);
        tag.putString("Status", "minecraft:full");
        CompoundTag section = new CompoundTag();
        section.putLongArray("data", new long[] {1L, 2L, 3L, Long.MAX_VALUE});
        tag.put("section", section);
        return tag;
    }

    @Test
    void vanillaReadsBackWhatThePoolCompressed() throws IOException {
        CompoundTag tag = chunkTag();
        ChunkPos pos = new ChunkPos(3, -7);
        Path folder = Files.createTempDirectory("leafs-region");
        RegionStorageInfo info = new RegionStorageInfo("test", Level.OVERWORLD, "chunk");
        try (RegionFile file = new RegionFile(info, folder.resolve("r.0.-1.mca"), folder, false)) {
            file.write(pos, CompressedChunk.of(tag).buffer());
        }

        try (RegionFileStorage storage = new RegionFileStorage(info, folder, false)) {
            assertEquals(tag, storage.read(pos));
        }
    }

    @Test
    void theBytesReadAndScanLikeAFile() throws IOException {
        CompoundTag tag = chunkTag();
        CompressedChunk compressed = CompressedChunk.of(tag);

        assertEquals(tag, compressed.read());

        CollectFields version = new CollectFields(new FieldSelector(IntTag.TYPE, "DataVersion"));
        compressed.scan(version);
        assertEquals(4882, ((CompoundTag) version.getResult()).getIntOr("DataVersion", 0));
    }
}
