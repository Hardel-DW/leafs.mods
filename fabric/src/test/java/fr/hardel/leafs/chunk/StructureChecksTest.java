package fr.hardel.leafs.chunk;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import fr.hardel.MinecraftBootstrap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.storage.ChunkScanAccess;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Roadmap, StructureCheck: the disk read of one worker must not hold every other search, and a chunk loaded meanwhile is fresher than its file. */
@ExtendWith(MinecraftBootstrap.class)
class StructureChecksTest {
    private static final ChunkPos POS = new ChunkPos(3, 3);
    private static final Identifier FORT = Identifier.fromNamespaceAndPath("test", "fort");
    private static final StructurePlacement PLACEMENT = new RandomSpreadStructurePlacement(32, 8, RandomSpreadType.LINEAR, 0);

    private final CountDownLatch scanning = new CountDownLatch(1);
    private final CountDownLatch scanRelease = new CountDownLatch(0);
    private final Structure fort = new Structure(new Structure.StructureSettings(HolderSet.empty())) {
        @Override
        protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
            return Optional.empty();
        }

        @Override
        public StructureType<?> type() {
            return StructureType.JIGSAW;
        }
    };

    private StructureCheck check(CountDownLatch release) {
        MappedRegistry<Structure> structures = new MappedRegistry<>(Registries.STRUCTURE, Lifecycle.stable());
        Registry.register(structures, FORT, fort);
        structures.freeze();
        Biome biome = new Biome.BiomeBuilder().hasPrecipitation(false).temperature(0).downfall(0)
            .specialEffects(new BiomeSpecialEffects.Builder().waterColor(0).build())
            .mobSpawnSettings(MobSpawnSettings.EMPTY).generationSettings(BiomeGenerationSettings.EMPTY).build();
        FixedBiomeSource biomes = new FixedBiomeSource(Holder.direct(biome));
        ChunkGenerator generator = new FlatLevelSource(new FlatLevelGeneratorSettings(Optional.empty(), Holder.direct(biome), List.of()));
        ChunkScanAccess disk = (pos, visitor) -> {
            scanning.countDown();
            await(release);
            fileWithAFort().acceptAsRoot(visitor);
            return CompletableFuture.completedFuture(null);
        };
        return new StructureCheck(disk, new RegistryAccess.ImmutableRegistryAccess(List.of(structures)), null, Level.OVERWORLD, generator, null, null, biomes, 0L, new DataFixer() {
            @Override
            public <T> Dynamic<T> update(DSL.TypeReference type, Dynamic<T> input, int version, int newVersion) {
                return input;
            }

            @Override
            public Schema getSchema(int key) {
                throw new UnsupportedOperationException();
            }
        });
    }

    private static CompoundTag fileWithAFort() {
        CompoundTag start = new CompoundTag();
        start.putString("id", FORT.toString());
        start.putInt("references", 1);
        CompoundTag starts = new CompoundTag();
        starts.put(FORT.toString(), start);
        CompoundTag structures = new CompoundTag();
        structures.put("starts", starts);
        CompoundTag chunk = new CompoundTag();
        chunk.put("structures", structures);
        return NbtUtils.addCurrentDataVersion(chunk);
    }

    @Test
    void theFileAnswersOnceThenTheCacheDoes() {
        StructureCheck check = check(scanRelease);

        assertEquals(StructureCheckResult.START_PRESENT, check.checkStart(POS, fort, PLACEMENT, false));
        assertEquals(StructureCheckResult.START_NOT_PRESENT, check.checkStart(POS, fort, PLACEMENT, true), "referenced already");
    }

    @Test
    void aDiskReadHoldsNobodyAndALoadedChunkOutranksItsFile() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        StructureCheck check = check(release);
        AtomicReference<StructureCheckResult> fromDisk = new AtomicReference<>();
        Thread reader = new Thread(() -> fromDisk.set(check.checkStart(POS, fort, PLACEMENT, false)));
        reader.start();
        assertTrue(scanning.await(5, TimeUnit.SECONDS));

        CountDownLatch loaded = new CountDownLatch(1);
        Thread loader = new Thread(() -> {
            check.onStructureLoad(POS, Map.of());
            loaded.countDown();
        });
        loader.start();
        assertTrue(loaded.await(2, TimeUnit.SECONDS), "the loaded starts landed while the disk was still being read");

        release.countDown();
        reader.join();
        assertEquals(StructureCheckResult.START_PRESENT, fromDisk.get(), "the reader answers from the file it read");
        assertEquals(StructureCheckResult.START_NOT_PRESENT, check.checkStart(POS, fort, PLACEMENT, false), "the loaded chunk, without a fort, outranks its file");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
