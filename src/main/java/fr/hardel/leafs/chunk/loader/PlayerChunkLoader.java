package fr.hardel.leafs.chunk.loader;

import fr.hardel.leafs.chunk.PropagatorAccess;
import fr.hardel.leafs.chunk.propagator.LevelTicketPropagator;
import fr.hardel.leafs.chunk.propagator.SimulationLevels;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player view pipeline, ticked by the player's owner. Rings from near to far, three stages (loaded, generated, ticking), paced disk loads. Simulation drains first: a chunk's region exists before its promotion is mailed. */
public final class PlayerChunkLoader {

    /** A burst of a few seconds of loads, for a teleport or a join. */
    private static final int BURST_TICKS = 8;
    private static final int MIN_CONCURRENT_LOADS = 5;

    private final ChunkMap chunkMap;
    private final StageTickets tickets;
    private final int loadsPerTick;
    private final LevelTicketPropagator propagator;
    private final SimulationLevels simulation;
    private final Map<ServerPlayer, PlayerViewState> states = new ConcurrentHashMap<>();

    public PlayerChunkLoader(ChunkMap chunkMap, StageTickets tickets, int loadsPerTick) {
        this.chunkMap = chunkMap;
        this.tickets = tickets;
        this.loadsPerTick = loadsPerTick;
        PropagatorAccess access = (PropagatorAccess) chunkMap.getDistanceManager();
        this.propagator = access.leafs$propagator();
        this.simulation = access.leafs$simulation();
    }

    public void tick(ServerPlayer player) {
        if (skip(player)) {
            removePlayer(player);
            return;
        }

        PlayerViewState state = states.computeIfAbsent(player, ignored -> new PlayerViewState());
        boolean posted = refreshView(player, state);
        LongArrayList newLoads = startLoads(state);
        if (posted || !newLoads.isEmpty()) {
            simulation.drain();
            propagator.drain();
        }

        requestLoads(newLoads);
        boolean progressed = progressLoading(state);
        progressed |= progressGenerating(state);
        if (progressed) {
            simulation.drain();
            propagator.drain();
        }
    }

    /** The chunk under the player is ticketed now, by the thread that moved him, so his region exists before anyone asks who owns him. The view pipeline keeps its own ticket. */
    public void follow(ServerPlayer player) {
        if (skip(player)) {
            return;
        }

        PlayerViewState state = states.computeIfAbsent(player, ignored -> new PlayerViewState());
        long chunk = player.chunkPosition().pack();
        long previous = state.standing.getAndSet(chunk);
        if (previous == chunk) {
            return;
        }

        tickets.acquire(chunk, StageTickets.TICK);
        releaseStanding(previous);
        simulation.drain();
        propagator.drain();
    }

    public void removePlayer(ServerPlayer player) {
        PlayerViewState state = states.remove(player);
        if (state == null) {
            return;
        }

        for (ObjectIterator<Long2ByteMap.Entry> iterator = state.stages.long2ByteEntrySet().fastIterator(); iterator.hasNext(); ) {
            Long2ByteMap.Entry entry = iterator.next();
            tickets.release(entry.getLongKey(), PlayerViewState.heldTicketStage(entry.getByteValue()));
        }

        releaseStanding(state.standing.getAndSet(ChunkPos.INVALID_CHUNK_POS));
    }

    private void releaseStanding(long chunk) {
        if (chunk != ChunkPos.INVALID_CHUNK_POS) {
            tickets.release(chunk, StageTickets.TICK);
        }
    }

    /** Retained chunks across every player of this level, for the regions command. */
    public int retainedChunks() {
        int total = 0;
        for (PlayerViewState state : states.values()) {
            total += state.stages.size();
        }

        return total;
    }

    private boolean skip(ServerPlayer player) {
        return player.isSpectator() && !chunkMap.level.getGameRules().get(GameRules.SPECTATORS_GENERATE_CHUNKS);
    }

    private boolean refreshView(ServerPlayer player, PlayerViewState state) {
        ChunkPos center = player.chunkPosition();
        int sendDistance = Mth.clamp(player.requestedViewDistance(), ChunkMap.MIN_VIEW_DISTANCE, chunkMap.serverViewDistance);
        int tickDistance = Math.min(chunkMap.getDistanceManager().simulationDistance, sendDistance);
        if (center.x() == state.centerX && center.z() == state.centerZ && sendDistance == state.sendDistance && tickDistance == state.tickDistance) {
            return false;
        }

        state.centerX = center.x();
        state.centerZ = center.z();
        state.sendDistance = sendDistance;
        state.loadDistance = sendDistance + 1;
        state.tickDistance = tickDistance;

        boolean posted = false;
        for (ObjectIterator<Long2ByteMap.Entry> iterator = state.stages.long2ByteEntrySet().fastIterator(); iterator.hasNext(); ) {
            Long2ByteMap.Entry entry = iterator.next();
            long chunk = entry.getLongKey();
            byte stage = entry.getByteValue();
            int distance = distanceToCenter(state, chunk);
            if (distance > state.loadDistance) {
                tickets.release(chunk, PlayerViewState.heldTicketStage(stage));
                state.loading.remove(chunk);
                state.generating.remove(chunk);
                iterator.remove();
                posted = true;
                continue;
            }

            posted |= alignStage(state, entry, chunk, stage, distance);
        }

        rebuildPending(state);
        return posted;
    }

    /** A ring shift moves a retained chunk's target stage. */
    private boolean alignStage(PlayerViewState state, Long2ByteMap.Entry entry, long chunk, byte stage, int distance) {
        if (stage == PlayerViewState.STAGE_TICK && distance > state.tickDistance) {
            tickets.swap(chunk, StageTickets.TICK, StageTickets.GENERATED);
            entry.setValue(PlayerViewState.STAGE_GENERATED);
            return true;
        }

        if ((stage == PlayerViewState.STAGE_GENERATING || stage == PlayerViewState.STAGE_GENERATED) && distance > state.sendDistance) {
            tickets.swap(chunk, StageTickets.GENERATED, StageTickets.LOADED);
            state.generating.remove(chunk);
            entry.setValue(PlayerViewState.STAGE_LOADED);
            return true;
        }

        if (stage == PlayerViewState.STAGE_LOADED && distance <= state.sendDistance) {
            tickets.swap(chunk, StageTickets.LOADED, StageTickets.GENERATED);
            entry.setValue(PlayerViewState.STAGE_GENERATING);
            state.generating.add(chunk);
            return true;
        }

        if (stage == PlayerViewState.STAGE_GENERATED && distance <= state.tickDistance) {
            tickets.swap(chunk, StageTickets.GENERATED, StageTickets.TICK);
            entry.setValue(PlayerViewState.STAGE_TICK);
            return true;
        }

        return false;
    }

    /** Ring order is the priority: the queue refills nearest first, no comparator over mutable state. */
    private void rebuildPending(PlayerViewState state) {
        state.pending.clear();
        for (int radius = 0; radius <= state.loadDistance; radius++) {
            if (radius == 0) {
                enqueueIfNew(state, state.centerX, state.centerZ);
                continue;
            }

            for (int x = state.centerX - radius; x <= state.centerX + radius; x++) {
                enqueueIfNew(state, x, state.centerZ - radius);
                enqueueIfNew(state, x, state.centerZ + radius);
            }
            for (int z = state.centerZ - radius + 1; z <= state.centerZ + radius - 1; z++) {
                enqueueIfNew(state, state.centerX - radius, z);
                enqueueIfNew(state, state.centerX + radius, z);
            }
        }
    }

    private void enqueueIfNew(PlayerViewState state, int chunkX, int chunkZ) {
        long chunk = ChunkPos.pack(chunkX, chunkZ);
        if (!state.stages.containsKey(chunk)) {
            state.pending.enqueue(chunk);
        }
    }

    private LongArrayList startLoads(PlayerViewState state) {
        state.loadBudget = Math.min(state.loadBudget + loadsPerTick, (double) loadsPerTick * BURST_TICKS);
        int concurrentCap = Math.max(MIN_CONCURRENT_LOADS, Mth.square(2 * state.loadDistance + 1) / 5);
        LongArrayList started = new LongArrayList();
        while (state.loadBudget >= 1.0 && state.loading.size() < concurrentCap && !state.pending.isEmpty()) {
            long chunk = state.pending.dequeueLong();
            if (state.stages.containsKey(chunk)) {
                continue;
            }

            state.stages.put(chunk, PlayerViewState.STAGE_LOADING);
            tickets.acquire(chunk, StageTickets.LOADED);
            state.loading.add(chunk);
            state.loadBudget -= 1.0;
            started.add(chunk);
        }

        return started;
    }

    /** The drain just materialised the holders; the EMPTY request is what makes the disk read happen. */
    private void requestLoads(LongArrayList newLoads) {
        for (int i = 0; i < newLoads.size(); i++) {
            long chunk = newLoads.getLong(i);
            propagator.scheduling().requestStatus(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), ChunkStatus.EMPTY);
        }
    }

    private boolean progressLoading(PlayerViewState state) {
        boolean posted = false;
        for (LongIterator iterator = state.loading.iterator(); iterator.hasNext(); ) {
            long chunk = iterator.nextLong();
            ChunkHolder holder = chunkMap.getUpdatingChunkIfPresent(chunk);
            if (holder == null || holder.getChunkIfPresentUnchecked(ChunkStatus.EMPTY) == null) {
                continue;
            }

            iterator.remove();
            if (distanceToCenter(state, chunk) <= state.sendDistance) {
                tickets.swap(chunk, StageTickets.LOADED, StageTickets.GENERATED);
                state.stages.put(chunk, PlayerViewState.STAGE_GENERATING);
                state.generating.add(chunk);
                posted = true;
            } else {
                state.stages.put(chunk, PlayerViewState.STAGE_LOADED);
            }
        }

        return posted;
    }

    private boolean progressGenerating(PlayerViewState state) {
        boolean posted = false;
        for (LongIterator iterator = state.generating.iterator(); iterator.hasNext(); ) {
            long chunk = iterator.nextLong();
            ChunkHolder holder = chunkMap.getUpdatingChunkIfPresent(chunk);
            if (holder == null || !(holder.getChunkIfPresentUnchecked(ChunkStatus.FULL) instanceof LevelChunk)) {
                continue;
            }

            iterator.remove();
            if (distanceToCenter(state, chunk) <= state.tickDistance) {
                tickets.swap(chunk, StageTickets.GENERATED, StageTickets.TICK);
                state.stages.put(chunk, PlayerViewState.STAGE_TICK);
                posted = true;
            } else {
                state.stages.put(chunk, PlayerViewState.STAGE_GENERATED);
            }
        }

        return posted;
    }

    private static int distanceToCenter(PlayerViewState state, long chunk) {
        return Math.max(Math.abs(ChunkPos.getX(chunk) - state.centerX), Math.abs(ChunkPos.getZ(chunk) - state.centerZ));
    }
}
