package fr.hardel.leafs.network;

import fr.hardel.leafs.entity.ServerLevelEntityAccess;
import fr.hardel.leafs.metrics.DeferReason;
import fr.hardel.leafs.scheduler.DeferredTransports;
import fr.hardel.leafs.scheduler.DeferredWork;
import fr.hardel.leafs.ticking.TickingBinding;
import fr.hardel.leafs.ticking.TickingManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The disconnect runs whole on the region that owns the player, nothing pauses; in place once the pool stopped. */
public final class PlayerTeardown {
    private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();

    private PlayerTeardown() {
    }

    /** A rejoin during the hop must not read the file the pending save has not written yet. */
    public static boolean pending(UUID id) {
        return PENDING.contains(id);
    }

    public static void run(ServerPlayer player, Runnable vanilla) {
        if (!(player.level() instanceof ServerLevel level) || TickingManager.of(level.getServer()).halted() || ((ServerLevelEntityAccess) level).leafs$entityLists().owns(player)) {
            vanilla.run();
            return;
        }

        UUID id = player.getUUID();
        PENDING.add(id);
        ChunkPos chunk = player.chunkPosition();
        DeferredTransports transports = TickingBinding.of(level);
        DeferredWork.owner(DeferReason.PLAYER_TEARDOWN, transports.stats(), chunk.x(), chunk.z(), () -> {
            try {
                vanilla.run();
            } finally {
                PENDING.remove(id);
            }
        }).submit(transports);
    }
}
