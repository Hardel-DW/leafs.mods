package fr.hardel.leafs.chunk.loader;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.world.level.ChunkPos;

import java.util.concurrent.atomic.AtomicLong;

/** One player's view, touched only by the thread that ticks him, except the chunk he stands on, which whoever moves him tickets. The in-flight sets are what the polls walk. */
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
    final AtomicLong standing = new AtomicLong(ChunkPos.INVALID_CHUNK_POS);

    int centerX;
    int centerZ;
    int sendDistance = -1;
    int loadDistance;
    int tickDistance;
    double loadBudget;

    /** The ref counted stage whose ticket a pipeline stage holds: one ticket per chunk at a time. */
    static int heldTicketStage(byte stage) {
        return switch (stage) {
            case STAGE_LOADING, STAGE_LOADED -> StageTickets.LOADED;
            case STAGE_GENERATING, STAGE_GENERATED -> StageTickets.GENERATED;
            case STAGE_TICK -> StageTickets.TICK;
            default -> throw new IllegalArgumentException("Unknown pipeline stage " + stage);
        };
    }
}
