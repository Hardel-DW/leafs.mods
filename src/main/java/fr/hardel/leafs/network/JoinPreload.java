package fr.hardel.leafs.network;

import com.mojang.authlib.GameProfile;
import fr.hardel.leafs.global.DeferredFileWrites;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.Util;
import net.minecraft.world.level.storage.LevelResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The playerdata tag read once by {@code PrepareSpawnTask.start} is kept for {@code Ready.spawn},
 * stats and advancements read off-thread during configuration: the flip into the game touches no
 * disk. Reads consult the pending writes of {@link DeferredFileWrites} first, never a stale file.
 */
public final class JoinPreload {
    private static final ConcurrentHashMap<UUID, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Path, CompletableFuture<Optional<String>>> CONTENT_BY_FILE = new ConcurrentHashMap<>();

    private record Entry(Optional<CompoundTag> playerData, Path statsFile, CompletableFuture<Optional<String>> stats, Path advancementsFile, CompletableFuture<Optional<String>> advancements) {
    }

    private JoinPreload() {
    }

    /** Keeps the first playerdata read and kicks the two JSON reads; a tag read while the previous session still tears down is not kept. */
    public static Optional<CompoundTag> captureAndPreload(PlayerList playerList, NameAndId nameAndId, Optional<CompoundTag> loaded) {
        discard(nameAndId);
        if (PlayerTeardown.pending(nameAndId.id())) {
            return loaded;
        }

        Path statsFile = ((PlayerListFileAccess) playerList).leafs$statsFile(new GameProfile(nameAndId.id(), nameAndId.name()));
        Path advancementsFile = playerList.getServer().getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR).resolve(nameAndId.id() + ".json");
        Entry entry = new Entry(loaded, statsFile, readAsync(statsFile), advancementsFile, readAsync(advancementsFile));
        ENTRIES.put(nameAndId.id(), entry);
        CONTENT_BY_FILE.put(statsFile, entry.stats());
        CONTENT_BY_FILE.put(advancementsFile, entry.advancements());
        return loaded;
    }

    /** The second {@code loadPlayerData} of the join, on the flip: served from the kept tag, then the entry retires. */
    public static Optional<CompoundTag> servePlayerData(NameAndId nameAndId, Supplier<Optional<CompoundTag>> fallback) {
        Entry entry = retire(nameAndId);
        return entry == null ? fallback.get() : entry.playerData();
    }

    /**
     * The single read hook of the player JSON files. A pending deferred write wins, because it is
     * newer than any preload; then a done preload for this file replaces the disk read, once; null
     * lets vanilla read the disk itself.
     */
    public static BufferedReader playerFileReader(Path file) {
        CompletableFuture<Optional<String>> content = CONTENT_BY_FILE.remove(file);
        DeferredFileWrites writes = DeferredFileWrites.active();
        String pending = writes == null ? null : writes.pendingText(file);
        if (pending != null) {
            return new BufferedReader(new StringReader(pending));
        }

        if (content == null || !content.isDone() || content.isCompletedExceptionally()) {
            return null;
        }

        return content.join().map(text -> new BufferedReader(new StringReader(text))).orElse(null);
    }

    public static void discard(NameAndId nameAndId) {
        retire(nameAndId);
    }

    private static Entry retire(NameAndId nameAndId) {
        Entry entry = ENTRIES.remove(nameAndId.id());
        if (entry != null) {
            CONTENT_BY_FILE.remove(entry.statsFile(), entry.stats());
            CONTENT_BY_FILE.remove(entry.advancementsFile(), entry.advancements());
        }

        return entry;
    }

    /** An unreadable file resolves empty: the constructor then reads the disk itself and applies vanilla's own error handling. */
    private static CompletableFuture<Optional<String>> readAsync(Path file) {
        DeferredFileWrites writes = DeferredFileWrites.active();
        String pending = writes == null ? null : writes.pendingText(file);
        if (pending != null) {
            return CompletableFuture.completedFuture(Optional.of(pending));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                return Files.isRegularFile(file) ? Optional.of(Files.readString(file, StandardCharsets.UTF_8)) : Optional.<String>empty();
            } catch (IOException exception) {
                return Optional.<String>empty();
            }
        }, Util.ioPool());
    }
}
