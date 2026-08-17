package fr.hardel.leafs.ticking;

import fr.hardel.leafs.metrics.MinuteCounter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Holding the barrier makes {@code enterTick} throw on the holder thread: the deterministic probe for "paused". */
@Timeout(10)
class PauseBatchTest {

    private final TickBarrier barrier = new TickBarrier();
    private final PauseBatch batch = new PauseBatch(barrier, new MinuteCounter());
    private final List<String> ran = new ArrayList<>();

    private void assertBarrierDown() {
        assertDoesNotThrow(() -> {
            barrier.enterTick();
            barrier.exitTick();
        });
    }

    @Test
    void outsideABatchEachRunPausesAndReleases() {
        batch.run(() -> ran.add("alone"));

        assertEquals(List.of("alone"), ran);
        assertBarrierDown();
    }

    @Test
    void insideABatchTheWaveSharesOnePauseUntilClose() {
        batch.open();
        batch.run(() -> ran.add("first"));
        assertThrows(IllegalStateException.class, barrier::enterTick, "the pause must survive between two removals of the wave");

        batch.run(() -> ran.add("second"));
        batch.close();

        assertEquals(List.of("first", "second"), ran);
        assertBarrierDown();
    }

    @Test
    void aBatchWithoutAnyRunNeverTouchesTheBarrier() {
        batch.open();
        batch.close();

        assertBarrierDown();
    }

    @Test
    void aThrowingRunKeepsThePauseForTheRestOfTheWave() {
        batch.open();
        assertThrows(IllegalStateException.class, () -> batch.run(() -> {
            throw new IllegalStateException("boom");
        }));

        assertThrows(IllegalStateException.class, barrier::enterTick, "the wave behind the failure still needs the pause");
        batch.run(() -> ran.add("survivor"));
        batch.close();

        assertEquals(List.of("survivor"), ran);
        assertBarrierDown();
    }
}
