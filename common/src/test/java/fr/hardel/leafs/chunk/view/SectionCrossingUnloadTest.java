package fr.hardel.leafs.chunk.view;

import fr.hardel.MinecraftBootstrap;
import fr.hardel.leafs.chunk.level.ChunkLevels;
import fr.hardel.leafs.chunk.level.LevelListener;
import fr.hardel.leafs.ticking.TickEpochs;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MinecraftBootstrap.class)
class SectionCrossingUnloadTest {
    private static final int VIEW = 12;
    private static final int LOADED_RADIUS = VIEW + ChunkLevel.MAX_LEVEL - ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING);

    private final TicketStorage tickets = new TicketStorage();
    private final ChunkLevels players = new ChunkLevels(34, new TickEpochs(0, () -> { }));
    private final ChunkLevels loading = new ChunkLevels(ChunkLevel.MAX_LEVEL + 2, new TickEpochs(0, () -> { }));
    private final ViewTickets view = new ViewTickets(tickets, players, VIEW, _ -> false, Integer.MAX_VALUE);
    private final PlayerSources sources = new PlayerSources(tickets, players, 5);

    @Test
    void aLoadingDrainBetweenTwoPlayerSectionsKeepsTheViewLoaded() throws InterruptedException {
        tickets.setLoadingChunkUpdatedListener((key, level, _) -> loading.setSource(ChunkPos.getX(key), ChunkPos.getZ(key), level));
        sources.enter(ChunkPos.pack(63, 0));
        players.drain(view);
        loading.drain((_, _, _) -> {});

        LongOpenHashSet unloaded = new LongOpenHashSet();
        LevelListener recorder = (key, old, now) -> {
            if (ChunkLevel.isLoaded(old) && !ChunkLevel.isLoaded(now)) {
                unloaded.add(key);
            }
        };
        LevelListener poolWorker = new LevelListener() {
            @Override
            public void changed(long chunkKey, int oldLevel, int newLevel) {
            }

            @Override
            public void published(Runnable pass) {
                Thread worker = new Thread(() -> loading.drain(recorder));
                worker.start();
                joinQuietly(worker);
            }
        };

        sources.leave(ChunkPos.pack(63, 0));
        sources.enter(ChunkPos.pack(64, 0));
        players.drain(view.and(poolWorker));
        loading.drain(recorder);

        unloaded.removeIf(key -> ChunkPos.getX(key) == 63 - LOADED_RADIUS);
        assertEquals(0, unloaded.size(), "chunks of the view unloaded while the player only walked one chunk");
    }

    private static void joinQuietly(Thread worker) {
        try {
            worker.join();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
