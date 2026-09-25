package fr.hardel.leafs.chunk.disk;

import fr.hardel.MinecraftBootstrap;
import fr.hardel.leafs.chunk.ChunkFixtures;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MinecraftBootstrap.class)
class CompressedChunkTest {
    @Test
    void vanillaReadsBackWhatThePoolCompressed() throws IOException {
        CompoundTag tag = ChunkFixtures.photo(4882);
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
}
