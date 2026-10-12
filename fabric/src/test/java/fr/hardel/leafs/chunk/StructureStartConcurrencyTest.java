package fr.hardel.leafs.chunk;

import com.mojang.serialization.MapCodec;
import fr.hardel.MinecraftBootstrap;
import fr.hardel.TestThreads;
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
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
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
        CountDownLatch firstInside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger overlaps = new AtomicInteger();
        Runnable body = () -> {
            if (inside.incrementAndGet() > 1) {
                overlaps.incrementAndGet();
            }

            firstInside.countDown();
            TestThreads.await(release);
            inside.decrementAndGet();
        };

        Thread one = Thread.ofPlatform().start(new Probe(StructureType.STRONGHOLD, body)::start);
        TestThreads.await(firstInside);
        Thread two = Thread.ofPlatform().start(new Probe(StructureType.STRONGHOLD, body)::start);
        TestThreads.awaitParked(two);
        int overlapsWhileTheFirstRuns = overlaps.get();
        release.countDown();
        one.join();
        two.join();

        assertEquals(0, overlapsWhileTheFirstRuns, "the second stronghold waits for the first");
        assertEquals(0, overlaps.get());
    }

    @Test
    void startsOfTwoTypesRunTogether() throws InterruptedException {
        assertEquals(2, startsThatMeet(StructureType.STRONGHOLD, StructureType.FORTRESS));
    }

    /** 2026-09-26: one lock for every jigsaw start left up to 11 of 12 pool workers waiting, up to 524 ms. */
    @Test
    void twoJigsawStartsRunTogether() throws InterruptedException {
        assertEquals(2, startsThatMeet(StructureType.JIGSAW, StructureType.JIGSAW));
    }

    // Each start waits inside its generation for the other one: two starts that cannot run together never meet and hang.
    private static int startsThatMeet(StructureType<?> first, StructureType<?> second) throws InterruptedException {
        CyclicBarrier both = new CyclicBarrier(2);
        AtomicInteger met = new AtomicInteger();
        Runnable body = () -> {
            meet(both);
            met.incrementAndGet();
        };

        Thread one = Thread.ofPlatform().start(new Probe(first, body)::start);
        Thread two = Thread.ofPlatform().start(new Probe(second, body)::start);
        one.join();
        two.join();
        return met.get();
    }

    private static void meet(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (InterruptedException | BrokenBarrierException exception) {
            throw new IllegalStateException(exception);
        }
    }

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
