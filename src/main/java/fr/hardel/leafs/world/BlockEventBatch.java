package fr.hardel.leafs.world;

import net.minecraft.world.level.BlockEventData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** Vanilla's runBlockEvents over one region's chunks: every chunk's events in posting order, cascades replayed until nothing is left. One per region. */
public final class BlockEventBatch<C> {
    private static final Comparator<Sequenced> ORDER = Comparator.comparingLong(Sequenced::sequence);

    private record Sequenced(long sequence, BlockEventData event) {
    }

    private final Function<C, ChunkBlockEvents> eventsOf;
    private final List<Sequenced> batch = new ArrayList<>();

    public BlockEventBatch(Function<C, ChunkBlockEvents> eventsOf) {
        this.eventsOf = eventsOf;
    }

    public void run(List<C> chunks, Predicate<C> ticking, Consumer<BlockEventData> runner) {
        do {
            batch.clear();
            for (C chunk : chunks) {
                ChunkBlockEvents events = eventsOf.apply(chunk);
                if (!events.isEmpty() && ticking.test(chunk)) {
                    events.drainTo((event, sequence) -> batch.add(new Sequenced(sequence, event)));
                }
            }

            batch.sort(ORDER);
            for (Sequenced sequenced : batch) {
                runner.accept(sequenced.event());
            }
        } while (!batch.isEmpty());
    }
}
