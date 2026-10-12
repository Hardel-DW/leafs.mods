package fr.hardel.leafs.gametest;

import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.disk.ChunkWrites;
import fr.hardel.leafs.chunk.pool.ChunkPool;
import fr.hardel.leafs.gametest.mixin.GameTestHelperAccessor;
import fr.hardel.leafs.ticking.TickingManager;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class FlushingSaveTest {
    private static final int FAR = 512;
    private static final int SIDE = 48;
    private static final TicketType LOADING = new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING);

    /** 2026-10-06: a flushing save returned while chunks no region owns were still unloading on a busy pool, not yet on disk. */
    @GameTest(environment = "leafs-gametest:flushing_save", maxTicks = HangGuard.TICKS)
    public void aFlushingSaveWritesTheChunksThatAreUnloading(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerChunkCache source = level.getChunkSource();
        ChunkWrites writes = LevelChunks.of(level).writes();
        ChunkPos corner = ChunkPos.containing(helper.absolutePos(BlockPos.ZERO));
        Ticket ticket = new Ticket(LOADING, ChunkLevel.byStatus(ChunkStatus.FULL));
        List<ChunkPos> square = new ArrayList<>();
        for (int x = 0; x < SIDE; x++) {
            for (int z = 0; z < SIDE; z++) {
                square.add(new ChunkPos(corner.x() + FAR + x, corner.z() + FAR + z));
            }
        }

        AtomicReference<HeldPool> busy = new AtomicReference<>();
        AtomicLong queued = new AtomicLong();
        List<CompletableFuture<Optional<CompoundTag>>> written = new ArrayList<>();
        helper.startSequence()
            .thenExecute(() -> square.forEach(pos -> source.addTicket(ticket, pos)))
            .thenWaitUntil(() -> helper.assertTrue(square.stream().allMatch(pos -> full(source, pos)), "the square is loaded"))
            .thenExecute(() -> square.forEach(pos -> source.ticketStorage.removeTicket(ticket, pos)))
            .thenWaitUntil(() -> helper.assertTrue(square.stream().anyMatch(pos -> source.chunkMap.getVisibleChunkIfPresent(pos.pack()) == null), "the square starts to unload"))
            .thenExecute(() -> busy.set(HeldPool.untilTheEndOf(helper, LevelChunks.of(level).pool())))
            .thenWaitUntil(() -> helper.assertTrue(busy.get().holdsEveryWorker(), "every chunk worker is busy"))
            .thenExecute(() -> {
                // The save pumps the server tasks while it waits for the unloads: the pool is freed there, never before the save.
                TickingManager.of(level.getServer()).globalScheduler().run(busy.get()::release);
                source.save(true);
                queued.set(square.stream().filter(pos -> writes.pending(pos) != null).count());
                square.forEach(pos -> written.add(source.chunkMap.worker.loadAsync(pos)));
            })
            .thenWaitUntil(() -> helper.assertTrue(written.stream().allMatch(CompletableFuture::isDone), "the square is read back from the disk"))
            .thenExecute(() -> {
                long missing = written.stream().filter(read -> read.join().isEmpty()).count();
                helper.assertTrue(queued.get() == 0 && missing == 0, "a flushing save leaves %s chunks of %s still queued for the disk and %s unwritten".formatted(queued.get(), square.size(), missing));
            })
            .thenSucceed();
    }

    private static boolean full(ServerChunkCache source, ChunkPos pos) {
        ChunkHolder holder = source.chunkMap.getVisibleChunkIfPresent(pos.pack());
        return holder != null && holder.getChunkIfPresent(ChunkStatus.FULL) != null;
    }

    /** Holds every chunk worker until released, so the pool runs nothing else meanwhile. The end of the test releases it too: a held worker never outlives its test. */
    private static final class HeldPool implements GameTestListener {
        private final CountDownLatch held;
        private final CountDownLatch released = new CountDownLatch(1);

        private HeldPool(ChunkPool pool) {
            held = new CountDownLatch(pool.threads());
            for (int worker = 0; worker < pool.threads(); worker++) {
                pool.execute(this::hold);
            }
        }

        private static HeldPool untilTheEndOf(GameTestHelper helper, ChunkPool pool) {
            HeldPool held = new HeldPool(pool);
            ((GameTestHelperAccessor) helper).leafs$testInfo().addListener(held);
            return held;
        }

        private boolean holdsEveryWorker() {
            return held.getCount() == 0;
        }

        private void release() {
            released.countDown();
        }

        private void hold() {
            held.countDown();
            awaitRelease();
        }

        private void awaitRelease() {
            try {
                released.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void testStructureLoaded(GameTestInfo testInfo) {
        }

        @Override
        public void testPassed(GameTestInfo testInfo, GameTestRunner runner) {
            release();
        }

        @Override
        public void testFailed(GameTestInfo testInfo, GameTestRunner runner) {
            release();
        }

        @Override
        public void testAddedForRerun(GameTestInfo original, GameTestInfo copy, GameTestRunner runner) {
        }
    }
}
