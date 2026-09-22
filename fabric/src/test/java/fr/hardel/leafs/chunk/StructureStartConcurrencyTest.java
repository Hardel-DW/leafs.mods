package fr.hardel.leafs.chunk;

import com.mojang.serialization.MapCodec;
import fr.hardel.MinecraftBootstrap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MinecraftBootstrap.class)
class StructureStartConcurrencyTest {
    private static final BiomeSource NO_BIOMES = new BiomeSource() {
        @Override
        protected MapCodec<? extends BiomeSource> codec() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected Stream<Holder<Biome>> collectPossibleBiomes() {
            return Stream.empty();
        }

        @Override
        public BiomeResolver createResolver(Climate.Sampler sampler) {
            return (_, _, _) -> null;
        }
    };

    /** 2026-09-22: STRUCTURE_STARTS declares no write radius, so two strongholds could assemble at once through the same static piece list. */
    @Test
    void twoStartsOfOneTypeNeverOverlap() throws InterruptedException {
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger overlaps = new AtomicInteger();
        Runnable body = () -> {
            if (inside.incrementAndGet() > 1) {
                overlaps.incrementAndGet();
            }

            sleep();
            inside.decrementAndGet();
        };

        runTogether(new Probe(StructureType.STRONGHOLD, body), new Probe(StructureType.STRONGHOLD, body));
        assertEquals(0, overlaps.get());
    }

    @Test
    void startsOfTwoTypesRunTogether() throws InterruptedException {
        CyclicBarrier both = new CyclicBarrier(2);
        AtomicInteger met = new AtomicInteger();
        Runnable body = () -> {
            try {
                both.await(5, TimeUnit.SECONDS);
                met.incrementAndGet();
            } catch (Exception exception) {
                Thread.currentThread().interrupt();
            }
        };

        runTogether(new Probe(StructureType.STRONGHOLD, body), new Probe(StructureType.FORTRESS, body));
        assertEquals(2, met.get());
    }

    private static void runTogether(Probe first, Probe second) throws InterruptedException {
        Thread one = Thread.ofPlatform().start(first::start);
        Thread two = Thread.ofPlatform().start(second::start);
        one.join(10_000);
        two.join(10_000);
    }

    private static void sleep() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /** A structure of a real type whose generation point only runs the probe. */
    private static final class Probe extends Structure {
        private final StructureType<?> type;
        private final Runnable body;

        private Probe(StructureType<?> type, Runnable body) {
            super(new StructureSettings(HolderSet.empty()));
            this.type = type;
            this.body = body;
        }

        private void start() {
            generate(Holder.direct(this), Level.OVERWORLD, null, null, NO_BIOMES, null, null, null, 0L, new ChunkPos(0, 0), 0, null, _ -> true);
        }

        @Override
        protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
            body.run();
            return Optional.empty();
        }

        @Override
        public StructureType<?> type() {
            return type;
        }
    }
}
