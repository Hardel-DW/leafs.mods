package fr.hardel.leafs.gametest;

import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.disk.ChunkWrites;
import fr.hardel.leafs.chunk.pool.ChunkPool;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public final class FlushingSaveTest {
    private static final int FAR = 512;
    private static final int SIDE = 48;
    private static final long BUSY_NANOS = TimeUnit.MILLISECONDS.toNanos(300);
    private static final TicketType LOADING = new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING);

    /** 2026-10-06: a flushing save returned while chunks no region owns were still unloading on a busy pool, not yet on disk. */
    @GameTest(maxTicks = 400)
    public void aFlushingSaveWritesTheChunksThatAreUnloading(GameTestHelper helper) {
        ServerChunkCache source = helper.getLevel().getChunkSource();
        ChunkWrites writes = LevelChunks.of(helper.getLevel()).writes();
        ChunkPool pool = LevelChunks.of(helper.getLevel()).pool();
        ChunkPos corner = ChunkPos.containing(helper.absolutePos(BlockPos.ZERO));
        Ticket ticket = new Ticket(LOADING, ChunkLevel.byStatus(ChunkStatus.FULL));
        List<ChunkPos> square = new ArrayList<>();
        for (int x = 0; x < SIDE; x++) {
            for (int z = 0; z < SIDE; z++) {
                square.add(new ChunkPos(corner.x() + FAR + x, corner.z() + FAR + z));
            }
        }

        helper.startSequence()
            .thenExecute(() -> {
                square.forEach(pos -> source.addTicket(ticket, pos));
                square.forEach(pos -> source.getChunk(pos.x(), pos.z(), ChunkStatus.FULL, true));
                square.forEach(pos -> source.ticketStorage.removeTicket(ticket, pos));
            })
            .thenWaitUntil(() -> helper.assertTrue(square.stream().anyMatch(pos -> source.chunkMap.getVisibleChunkIfPresent(pos.pack()) == null), "the square starts to unload"))
            .thenExecute(() -> {
                for (int worker = 0; worker < pool.threads(); worker++) {
                    pool.execute(() -> LockSupport.parkNanos(BUSY_NANOS));
                }

                source.save(true);
                long queued = square.stream().filter(pos -> writes.pending(pos) != null).count();
                long missing = square.stream().filter(pos -> source.chunkMap.worker.loadAsync(pos).join().isEmpty()).count();
                helper.assertTrue(queued == 0 && missing == 0, "a flushing save leaves %s chunks of %s still queued for the disk and %s unwritten".formatted(queued, square.size(), missing));
            })
            .thenSucceed();
    }
}
