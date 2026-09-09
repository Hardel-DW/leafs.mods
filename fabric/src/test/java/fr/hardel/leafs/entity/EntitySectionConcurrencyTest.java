package fr.hardel.leafs.entity;

import net.minecraft.util.ClassInstanceMultiMap;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A region reads an entity section across a seam while its owner writes it: the walk never tears, the class cache never misses an entity. */
class EntitySectionConcurrencyTest {
    private static final int WRITES = 20_000;

    private record Mob(int id) {
    }

    @Test
    void readersWalkWhileTheOwnerAddsAndRemoves() throws InterruptedException {
        ClassInstanceMultiMap<Object> section = new ClassInstanceMultiMap<>(Object.class);
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> readerFailure = new AtomicReference<>();
        Thread reader = new Thread(() -> {
            try {
                while (done.getCount() > 0) {
                    for (Object entity : section.find(Mob.class)) {
                        assertTrue(entity instanceof Mob);
                    }

                    for (Object ignored : section) {
                    }
                }
            } catch (Throwable throwable) {
                readerFailure.set(throwable);
            }
        });
        reader.start();

        for (int i = 0; i < WRITES; i++) {
            Mob mob = new Mob(i);
            section.add(mob);
            section.add("not a mob " + i);
            section.remove(mob);
        }

        done.countDown();
        reader.join(TimeUnit.SECONDS.toMillis(10));
        assertNull(readerFailure.get());
        assertEquals(WRITES, section.size());
        Collection<Mob> mobs = section.find(Mob.class);
        assertTrue(mobs.isEmpty(), "every mob was removed again, the class list followed");
    }
}
