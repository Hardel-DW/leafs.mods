package fr.hardel.leafs.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.capabilities.CapabilityListenerHolder;
import net.neoforged.neoforge.capabilities.ICapabilityInvalidationListener;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public final class CapabilityListenerTest {
    private static final int THREADS = 8;
    private static final int CHUNKS = 4096;
    private static final Executor DAEMONS = task -> Thread.ofPlatform().daemon().start(task);

    /** 2026-09-25: regions and the server thread wrote the fastutil maps of the capability listener holder without a lock. */
    public static void everyListenerHearsItsInvalidation(GameTestHelper helper) {
        CapabilityListenerHolder holder = new CapabilityListenerHolder();
        List<CompletableFuture<List<Invalidation>>> threads = IntStream.range(0, THREADS)
                .mapToObj(offset -> CompletableFuture.supplyAsync(() -> register(holder, offset), DAEMONS))
                .toList();
        long missed = threads.stream()
                .flatMap(thread -> thread.orTimeout(30, TimeUnit.SECONDS).join().stream())
                .filter(invalidation -> !invalidation.received)
                .count();
        helper.assertTrue(missed == 0, "%s of %s listeners missed their invalidation".formatted(missed, THREADS * CHUNKS));
        helper.succeed();
    }

    private static List<Invalidation> register(CapabilityListenerHolder holder, int offset) {
        return IntStream.range(0, CHUNKS).mapToObj(chunk -> {
            BlockPos pos = new BlockPos(chunk * 16 + offset, 64, 0);
            Invalidation invalidation = new Invalidation();
            holder.addListener(pos, invalidation);
            if (chunk % 2 == 0) {
                holder.invalidatePos(pos);
            } else {
                holder.invalidateChunk(ChunkPos.containing(pos));
            }
            holder.clean();
            return invalidation;
        }).toList();
    }

    private static final class Invalidation implements ICapabilityInvalidationListener {
        private volatile boolean received;

        @Override
        public boolean onInvalidate() {
            received = true;
            return true;
        }
    }
}
