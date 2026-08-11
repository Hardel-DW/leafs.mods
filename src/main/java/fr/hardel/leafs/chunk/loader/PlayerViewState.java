package fr.hardel.leafs.chunk.loader;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * One player's view pipeline, touched only by the thread that ticks the player. The stage map holds
 * every chunk the player retains a ticket on, the pending queue feeds new chunks in ring order, and
 * the two in-flight sets are what the progress polls walk instead of the whole map.
 */
final class PlayerViewState {

    static final byte STAGE_LOADING = 1;
    static final byte STAGE_LOADED = 2;
    static final byte STAGE_GENERATING = 3;
    static final byte STAGE_GENERATED = 4;
    static final byte STAGE_TICK = 5;

    final Long2ByteOpenHashMap stages = new Long2ByteOpenHashMap();
    final LongArrayFIFOQueue pending = new LongArrayFIFOQueue();
    final LongOpenHashSet loading = new LongOpenHashSet();
    final LongOpenHashSet generating = new LongOpenHashSet();

    int centerX;
    int centerZ;
    int sendDistance = -1;
    int loadDistance;
    int tickDistance;
    double loadBudget;

    /** The refcounted stage whose ticket a pipeline stage holds: one ticket per chunk at a time. */
    static int heldTicketStage(byte stage) {
        return switch (stage) {
            case STAGE_LOADING, STAGE_LOADED -> StageTickets.LOADED;
            case STAGE_GENERATING, STAGE_GENERATED -> StageTickets.GENERATED;
            case STAGE_TICK -> StageTickets.TICK;
            default -> throw new IllegalArgumentException("Unknown pipeline stage " + stage);
        };
    }
}
