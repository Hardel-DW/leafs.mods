package fr.hardel.leafs.world;

import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ObjLongConsumer;

/** Vanilla's block event set, per chunk. Each event keeps the level-wide sequence it arrived with, so a region replays vanilla's FIFO across its chunks. */
public final class ChunkBlockEvents {
    private final Map<BlockEventData, Long> events = new LinkedHashMap<>();

    /** Same event twice in a tick collapses onto the first, as vanilla's set does. */
    public void add(BlockEventData event, long sequence) {
        events.putIfAbsent(event, sequence);
    }

    public boolean isEmpty() {
        return events.isEmpty();
    }

    /** Hands every pending event over and empties the chunk; cascades land back here for the next pass. */
    public void drainTo(ObjLongConsumer<BlockEventData> output) {
        if (events.isEmpty()) {
            return;
        }

        List<Map.Entry<BlockEventData, Long>> drained = new ArrayList<>(events.entrySet());
        events.clear();
        for (Map.Entry<BlockEventData, Long> entry : drained) {
            output.accept(entry.getKey(), entry.getValue());
        }
    }

    public void clearArea(BoundingBox area) {
        events.keySet().removeIf(event -> area.isInside(event.pos()));
    }
}
