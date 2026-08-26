package fr.hardel.leafs.global;

import com.mojang.serialization.MapCodec;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.timers.TimerCallback;
import net.minecraft.world.level.timers.TimerQueue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 2026-08-20: the hook deferred the whole drain and opened the window every tick; only a due callback may. */
class TimerQueueDueTest {

    private record RecordingCallback(List<Long> calls) implements TimerCallback<Object> {
        @Override
        public void handle(Object context, TimerQueue<Object> queue, long time) {
            calls.add(time);
        }

        @Override
        public MapCodec<RecordingCallback> codec() {
            throw new UnsupportedOperationException("Never serialized in this test");
        }
    }

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void nothingDueRunsNothing() {
        List<Long> calls = new ArrayList<>();
        TimerQueue<Object> queue = new TimerQueue<>();
        queue.schedule("test", 50, new RecordingCallback(calls));

        queue.tick(new Object(), 49);

        assertEquals(List.of(), calls);
    }

    @Test
    void dueCallbackStillRunsThroughTheWrap() {
        List<Long> calls = new ArrayList<>();
        TimerQueue<Object> queue = new TimerQueue<>();
        queue.schedule("test", 50, new RecordingCallback(calls));

        queue.tick(new Object(), 50);

        assertEquals(List.of(50L), calls);
    }
}
