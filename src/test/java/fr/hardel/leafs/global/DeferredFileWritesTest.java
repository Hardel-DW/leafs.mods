package fr.hardel.leafs.global;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeferredFileWritesTest {

    /** Queues tasks until pumped, so the tests control exactly when the disk write happens. */
    private static final class ManualExecutor extends AbstractExecutorService {
        private final ArrayDeque<Runnable> queued = new ArrayDeque<>();

        void pumpOne() {
            queued.removeFirst().run();
        }

        void pumpAll() {
            while (!queued.isEmpty()) {
                pumpOne();
            }
        }

        @Override
        public void execute(Runnable task) {
            queued.addLast(task);
        }

        @Override
        public void shutdown() {
        }

        @Override
        public List<Runnable> shutdownNow() {
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return queued.isEmpty();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return queued.isEmpty();
        }
    }

    private final ManualExecutor executor = new ManualExecutor();
    private final DeferredFileWrites writes = new DeferredFileWrites(executor);

    @Test
    void pendingTextVisibleUntilWritten(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("stats.json");
        writes.writeText(file, "content");

        assertEquals("content", writes.pendingText(file));
        executor.pumpAll();
        assertNull(writes.pendingText(file));
        assertEquals("content", Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void lastWriteWinsAcrossPartialPumps(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("stats.json");
        writes.writeText(file, "first");
        writes.writeText(file, "second");

        assertEquals("second", writes.pendingText(file));
        executor.pumpOne();
        assertEquals("second", writes.pendingText(file));
        executor.pumpAll();
        assertEquals("second", Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void textWriterEnqueuesOnClose(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("advancements.json");
        try (Writer writer = writes.textWriter(file)) {
            writer.write("json");
        }

        assertEquals("json", writes.pendingText(file));
        executor.pumpAll();
        assertEquals("json", Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void nbtReplacesAtomicallyAndClearsPending(@TempDir Path dir) throws IOException {
        Path real = dir.resolve("player.dat");
        Path tmp = dir.resolve("player-tmp.dat");
        Path old = dir.resolve("player.dat_old");
        CompoundTag tag = new CompoundTag();
        tag.putInt("XpLevel", 30);
        writes.writeNbt(real, tmp, old, tag);

        assertEquals(tag, writes.pendingNbt(real));
        executor.pumpAll();
        assertNull(writes.pendingNbt(real));
        assertTrue(Files.isRegularFile(real));
        assertEquals(tag, NbtIo.readCompressed(real, NbtAccounter.unlimitedHeap()));
    }
}
