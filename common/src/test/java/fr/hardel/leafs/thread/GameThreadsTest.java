package fr.hardel.leafs.thread;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameThreadsTest {
    private static final ThreadGroup GROUP = Thread.currentThread().getThreadGroup();

    @Test
    void aThreadOutsideLeafsIsNoGameThread() throws InterruptedException {
        assertEquals(List.of(false), answers(body -> new Thread(body, "mod"), () -> List.of(GameThreads.isGame())));
    }

    @Test
    void aRegionWorkerIsAlwaysAGameThread() throws InterruptedException {
        assertEquals(List.of(true), answers(body -> new RegionWorker(GROUP, body, 1), () -> List.of(GameThreads.isGame())));
    }

    /** 2026-10-16: a chunk worker building a FULL chunk answered isSameThread false to Open Parties and Claims, which crashed. */
    @Test
    void aChunkWorkerIsAGameThreadOnlyWhileItHoldsAChunk() throws InterruptedException {
        List<Boolean> seen = answers(body -> new ChunkWorker(GROUP, body, 1), () -> {
            boolean generating = GameThreads.isGame();
            GameThreads.heldChunk();
            GameThreads.heldChunk();
            GameThreads.releasedChunk();
            boolean holdingOne = GameThreads.isGame();
            GameThreads.releasedChunk();
            return List.of(generating, holdingOne, GameThreads.isGame());
        });

        assertEquals(List.of(false, true, false), seen);
    }

    @Test
    void theLoopsOfTheServerThreadAreSharedByEveryGameThread() throws InterruptedException {
        Thread server = new Thread(GameThreads::bindServer, "Server thread");
        server.start();
        server.join();
        Thread render = new Thread(() -> {
        }, "Render thread");

        assertEquals(List.of(true, false), answers(body -> new RegionWorker(GROUP, body, 1), () -> List.of(GameThreads.sameThreadAs(server), GameThreads.sameThreadAs(render))));
        assertEquals(List.of(false), answers(body -> new Thread(body, "mod"), () -> List.of(GameThreads.sameThreadAs(server))));
    }

    private static List<Boolean> answers(Function<Runnable, Thread> threads, Supplier<List<Boolean>> questions) throws InterruptedException {
        AtomicReference<List<Boolean>> seen = new AtomicReference<>();
        Thread thread = threads.apply(() -> seen.set(questions.get()));
        thread.start();
        thread.join();
        return seen.get();
    }
}
