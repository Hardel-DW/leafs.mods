package fr.hardel.leafs.entity;

import fr.hardel.leafs.chunk.DegradedChunkReads;
import fr.hardel.leafs.metrics.DeferReason;
import fr.hardel.leafs.ownership.OwnershipViolationException;
import fr.hardel.leafs.scheduler.DeferredTransports;
import fr.hardel.leafs.scheduler.DeferredWork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PortalProcessor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.portal.TeleportTransition;

import java.util.function.Function;

/** Vanilla's teleport runs whole on the origin's owner; the add and remove primitives it calls carry the arrival to the target's owner. */
public final class EntityTeleports {
    private static final int PORTAL_SEARCH_BUDGET = 100;
    private final ServerLevel level;
    private final Function<ServerLevel, DeferredTransports> transportsOf;

    public EntityTeleports(ServerLevel level, Function<ServerLevel, DeferredTransports> transportsOf) {
        this.level = level;
        this.transportsOf = transportsOf;
    }

    /** False when the caller owns the origin, so vanilla runs in place; a foreign caller hands the move over and gets null. */
    public boolean route(Entity entity, TeleportTransition transition) {
        DeferredTransports transports = transportsOf.apply(level);
        ChunkPos origin = entity.chunkPosition();
        if (transports.owns(origin.x(), origin.z())) {
            return false;
        }

        DeferReason reason = entity instanceof ServerPlayer ? DeferReason.PLAYER_TELEPORT : DeferReason.TELEPORT;
        DeferredWork.owner(reason, transports.stats(), origin.x(), origin.z(), () -> entity.teleport(transition))
            .validIf(() -> stillTeleportable(entity))
            .submit(transports);

        return true;
    }

    /** The search starts on the origin's owner; a portal to create hops to the target's owner, and the teleport comes back through {@link #route}. */
    public void deferPortal(Entity entity, PortalProcessor process) {
        searchOn(level, entity.chunkPosition(), entity, process);
    }

    private void searchOn(ServerLevel where, ChunkPos chunk, Entity entity, PortalProcessor process) {
        DeferredTransports transports = transportsOf.apply(where);
        DeferredWork.owner(DeferReason.PORTAL, transports.stats(), chunk.x(), chunk.z(), () -> searchAndEnterPortal(entity, process))
            .validIf(() -> stillTeleportable(entity))
            .degraded(PORTAL_SEARCH_BUDGET)
            .submit(transports);
    }

    /** Vanilla's handlePortal tail; the teleport escapes the degraded scope so a refusal never cuts it mid-move. */
    private void searchAndEnterPortal(Entity entity, PortalProcessor process) {
        TeleportTransition transition;
        try {
            transition = process.getPortalDestination(level, entity);
        } catch (OwnershipViolationException refusal) {
            if (refusal.kind() != OwnershipViolationException.Kind.FOREIGN) {
                throw refusal;
            }

            searchOn(refusal.foreignLevel(), refusal.foreignChunk(), entity, process);
            return;
        }

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
