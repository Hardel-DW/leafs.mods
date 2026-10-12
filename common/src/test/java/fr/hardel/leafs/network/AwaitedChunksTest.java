package fr.hardel.leafs.network;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AwaitedChunksTest {
    private static final long CHUNK = 7L;

    private final AwaitedChunks awaited = new AwaitedChunks();
    private final List<Runnable> playerQueue = new ArrayList<>();
    private int sent;
    private boolean sendable;

    @Test
    void aChunkReadyBeforeItEntersTheViewIsSentOnce() {
        becomeSendable();
        enterTheView(() -> { });

        assertEquals(1, sentAfterThePlayerQueue());
    }

    /** 2026-10-01: a chunk that became sendable just after its player's view read it was sent by nobody and stayed a hole. */
    @Test
    void aChunkReadyAfterItEnteredTheViewIsSentOnce() {
        enterTheView(() -> { });
        becomeSendable();

        assertEquals(1, sentAfterThePlayerQueue());
    }

    @Test
    void aChunkThatBecomesSendableWhileTheViewReadsItIsSentOnce() {
        enterTheView(this::becomeSendable);

        assertEquals(1, sentAfterThePlayerQueue());
    }

    @Test
    void aChunkThatLeftTheViewBeforeItsAnnounceRunsIsNeverSent() {
        enterTheView(() -> { });
        becomeSendable();
        awaited.take(CHUNK);

        assertEquals(0, sentAfterThePlayerQueue());
    }

    private void enterTheView(Runnable whileReading) {
        if (awaited.entered(CHUNK, _ -> readSendable(whileReading) ? CHUNK : null) != null) {
            sent++;
        }
    }

    private boolean readSendable(Runnable whileReading) {
        whileReading.run();
        return sendable;
    }

    private void becomeSendable() {
        sendable = true;
        if (awaited.awaits(CHUNK)) {
            playerQueue.add(() -> {
                if (awaited.take(CHUNK)) {
                    sent++;
                }
            });
        }
    }

    private int sentAfterThePlayerQueue() {
        playerQueue.forEach(Runnable::run);
        return sent;
    }
}
