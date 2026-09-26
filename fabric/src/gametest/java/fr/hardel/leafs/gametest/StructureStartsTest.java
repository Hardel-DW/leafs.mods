package fr.hardel.leafs.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

public final class StructureStartsTest {
    private static final int POSITIONS = 16;
    private static final int COPIES = 4;

    @GameTest(maxTicks = 100)
    public void everyStructureStartsAlikeAloneAndInParallel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> differing = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(12)) {
            level.registryAccess().lookupOrThrow(Registries.STRUCTURE).listElements().forEach(structure -> {
                List<CompoundTag> alone = IntStream.range(0, POSITIONS).mapToObj(index -> start(level, structure, index)).toList();
                List<CompletableFuture<Boolean>> together = IntStream.range(0, POSITIONS * COPIES)
                    .mapToObj(index -> CompletableFuture.supplyAsync(() -> start(level, structure, index % POSITIONS).equals(alone.get(index % POSITIONS)), pool))
                    .toList();
                if (!together.stream().allMatch(CompletableFuture::join)) {
                    differing.add(structure.key().identifier().toString());
                }
            });
        }

        helper.assertTrue(differing.isEmpty(), "starts generated in parallel match the ones generated alone, except %s".formatted(differing));
        helper.succeed();
    }

    private static CompoundTag start(ServerLevel level, Holder<Structure> structure, int index) {
        ChunkPos pos = new ChunkPos(1000 + index * 37, -700 + index * 53);
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        return structure.value().generate(structure, level.dimension(), level.registryAccess(), generator, generator.getBiomeSource(),
                state.randomState(), level.getStructureManager(),
                state.getLevelSeed(), pos, 0, level, _ -> true)
            .createTag(StructurePieceSerializationContext.fromLevel(level), pos);
    }
}
