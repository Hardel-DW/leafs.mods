package fr.hardel.leafs.entity;

import fr.hardel.leafs.chunk.DegradedChunkReads;
import fr.hardel.leafs.metrics.DeferReason;
import fr.hardel.leafs.scheduler.DeferredTransports;
import fr.hardel.leafs.scheduler.DeferredWork;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PortalProcessor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.portal.TeleportTransition;

// One funnel for every move a region worker may not run in place; each routed move replays vanilla's Entity.teleport at its destination, serial same-level, window for a dimension change.
public final class EntityTeleports {

    // Attempts are readiness-gated phases (one per demanded area), not ticks; past the budget the net is vanilla's synchronous load.
    private static final int PORTAL_PHASE_BUDGET = 100;
    private final ServerLevel level;
    private final DeferredTransports transports;

    public EntityTeleports(ServerLevel level, DeferredTransports transports) {
        this.level = level;
        this.transports = transports;
    }

    // False when the vanilla path is safe in place; otherwise the move has been routed.
    public boolean route(Entity entity, TeleportTransition transition) {
        ServerLevel target = transition.newLevel();
        ChunkPos origin = entity.chunkPosition();
        int destinationX = SectionPos.posToSectionCoord(transition.position().x());
        int destinationZ = SectionPos.posToSectionCoord(transition.position().z());
        if (target == level && transports.owns(origin.x(), origin.z()) && transports.owns(destinationX, destinationZ)) {
            return false;
        }

        DeferReason reason = entity instanceof ServerPlayer ? DeferReason.PLAYER_TELEPORT : DeferReason.TELEPORT;
        DeferredWork move = target == level
            ? DeferredWork.serial(reason, transports.stats(), () -> entity.teleport(transition))
            : DeferredWork.window(reason, transports.stats(), () -> entity.teleport(transition));
        move.validIf(() -> stillTeleportable(entity)).submit(transports);

        return true;
    }

    // The processor carries the portal and entry position, so vanilla may drop entity.portalProcess without killing the traversal; the search writes foreign-dimension blocks, window work.
    public void deferPortal(Entity entity, PortalProcessor process) {
        DeferredWork.window(DeferReason.PORTAL, transports.stats(), () -> searchAndEnterPortal(entity, process))
            .validIf(() -> stillTeleportable(entity))
            .degradedWithSyncNet(PORTAL_PHASE_BUDGET)
            .submit(transports);
    }

    // Vanilla's handlePortal tail; the teleport escapes the degraded scope so a refusal can never cut it mid-move.
    private void searchAndEnterPortal(Entity entity, PortalProcessor process) {
        TeleportTransition transition = process.getPortalDestination(level, entity);
        if (transition == null) {
            return;
        }

        ServerLevel target = transition.newLevel();
        if (level.isAllowedToEnterPortal(target) && (target.dimension() == level.dimension() || entity.canTeleport(level, target))) {
            DegradedChunkReads.escape(() -> entity.teleport(transition));
        }
    }

    private boolean stillTeleportable(Entity entity) {
        if (entity instanceof ServerPlayer player && player.hasDisconnected()) {
            return false;
        }

        return !entity.isRemoved() && entity.level() == level;
    }
}
