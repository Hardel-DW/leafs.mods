package fr.hardel.leafs.global;

import fr.hardel.leafs.Leafs;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.FileUtil;
import net.minecraft.util.Util;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Disk half of the player saves, one writer thread in submission order. Reads see the pending payload first; without an active instance callers write synchronously. */
public final class DeferredFileWrites {

    private static volatile DeferredFileWrites active;

    private final ConcurrentHashMap<Path, Object> pending = new ConcurrentHashMap<>();
    private final ExecutorService executor;

    DeferredFileWrites(ExecutorService executor) {
        this.executor = executor;
    }

    public static DeferredFileWrites active() {
        return active;
    }

    public static void start() {
        active = new DeferredFileWrites(Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "Leafs IO Writer");
            thread.setDaemon(true);
            return thread;
        }));
    }

    public static void stopAndFlush() {
        DeferredFileWrites current = active;
        active = null;
        if (current != null) {
            current.close();
        }
    }

    public void writeNbt(Path realFile, Path tmpFile, Path oldFile, CompoundTag tag) {
        pending.put(realFile, tag);
        executor.execute(() -> {
            try {
                NbtIo.writeCompressed(tag, tmpFile);
                Util.safeReplaceFile(realFile, tmpFile, oldFile);
            } catch (Exception exception) {
                Leafs.LOGGER.warn("Deferred write of {} failed", realFile, exception);
            } finally {
                pending.remove(realFile, tag);
            }
        });
    }

    public void writeText(Path file, String content) {
        pending.put(file, content);
        executor.execute(() -> {
            try {
                FileUtil.createDirectoriesSafe(file.getParent());
                Files.writeString(file, content, StandardCharsets.UTF_8);
            } catch (IOException exception) {
                Leafs.LOGGER.warn("Deferred write of {} failed", file, exception);
            } finally {
                pending.remove(file, content);
            }
        });
    }

    /** A Writer whose close hands the collected text to {@link #writeText}; serialization stays on the caller. */
    public Writer textWriter(Path file) {
        return new StringWriter() {
            @Override
            public void close() {
                writeText(file, toString());
            }
        };
    }

    public CompoundTag pendingNbt(Path file) {
        return pending.get(file) instanceof CompoundTag tag ? tag : null;
    }

    public String pendingText(Path file) {
        return pending.get(file) instanceof String text ? text : null;
    }

    private void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                Leafs.LOGGER.error("Leafs IO writer did not flush within a minute, {} files may be stale", pending.size());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
