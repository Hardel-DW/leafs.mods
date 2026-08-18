package fr.hardel.leafs.entity;

import fr.hardel.leafs.metrics.DeferReason;
import fr.hardel.leafs.scheduler.DeferredTransports;
import fr.hardel.leafs.scheduler.DeferredWork;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.PortalProcessor;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.portal.TeleportTransition;

import java.util.ArrayList;
import java.util.List;

/** Routes teleports a region worker may not run in place: serial for same-level out-of-region, pipeline for cross-dimension. */
public final class EntityTeleports {

    private static final int PORTAL_SEARCH_WINDOW_BUDGET = 100;

    private record TeleportedNode(Entity entity, PositionMoveRotation currentValues, TeleportTransition transition, int parentIndex) {
    }

    private final ServerLevel level;
    private final DeferredTransports transports;
    private final PendingTeleports<List<TeleportedNode>> pending;

    public EntityTeleports(ServerLevel level, DeferredTransports transports) {
        this.level = level;
        this.transports = transports;
        this.pending = new PendingTeleports<>((chunkX, chunkZ, placement) -> transports.toOwner(DeferReason.TELEPORT, chunkX, chunkZ, placement));
    }

    /** Shutdown path: places everything still in flight toward this level, before the worlds save. */
    public void completeAll() {
        pending.completeAll();
    }

    public int pendingCount() {
        return pending.pendingCount();
    }

    /** Returns false when the vanilla path is safe; otherwise the move has been routed. */
    public boolean divertFromRegion(Entity entity, TeleportTransition transition) {
        ServerLevel target = transition.newLevel();
        int destinationX = SectionPos.posToSectionCoord(transition.position().x());
        int destinationZ = SectionPos.posToSectionCoord(transition.position().z());
        if (target == level && transports.owns(destinationX, destinationZ)) {
            return false;
        }

        if (target == level) {
            DeferredWork.serial(DeferReason.TELEPORT, transports.stats(), () -> entity.teleport(transition))
                .validIf(() -> !entity.isRemoved() && entity.level() == level)
                .submit(transports);

            return true;
        }

        beginCrossDimension(entity, transition);

        return true;
    }

    /** Defers to serial unless the current region owns both the player and the destination. */
    public boolean divertPlayerFromRegion(ServerPlayer player, TeleportTransition transition) {
        if (transition.newLevel() == level
            && transports.owns(player.chunkPosition().x(), player.chunkPosition().z())
            && transports.owns(SectionPos.posToSectionCoord(transition.position().x()), SectionPos.posToSectionCoord(transition.position().z()))) {
            return false;
        }

        DeferredWork.serial(DeferReason.PLAYER_TELEPORT, transports.stats(), () -> player.teleport(transition))
            .validIf(() -> !player.hasDisconnected() && !player.isRemoved() && player.level() == level)
            .submit(transports);

        return true;
    }

    /**
     * The search writes blocks in an unknown dimension, so it is window work; the engine's degraded
     * retry keeps the window from generating, and its budgeted last attempt is the synchronous net.
     */
    public void deferPortal(Entity entity) {
        DeferredWork.window(DeferReason.PORTAL, transports.stats(), () -> searchAndEnterPortal(entity))
            .validIf(() -> !entity.isRemoved() && entity.level() == level && entity.portalProcess != null)
            .degradedWithSyncNet(PORTAL_SEARCH_WINDOW_BUDGET)
            .submit(transports);
    }

    /** The vanilla tail of {@code handlePortal}: destination search, entry test, teleport. */
    private void searchAndEnterPortal(Entity entity) {
        PortalProcessor process = entity.portalProcess;
        TeleportTransition transition = process.getPortalDestination(level, entity);
        if (transition == null) {
            return;
        }

        ServerLevel target = transition.newLevel();
        if (level.isAllowedToEnterPortal(target) && (target.dimension() == level.dimension() || entity.canTeleport(level, target))) {
            entity.teleport(transition);
        }
    }

    private void beginCrossDimension(Entity entity, TeleportTransition transition) {
        ServerLevel target = transition.newLevel();
        List<TeleportedNode> nodes = new ArrayList<>();
        copyTree(entity, transition, -1, target, nodes);
        teleportSpectators(entity, transition);
        if (nodes.isEmpty()) {
            return;
        }

        EntityTeleports arrivals = ((ServerLevelEntityAccess) target).leafs$entityTeleports();
        ChunkPos originChunk = entity.chunkPosition();
        int destinationX = SectionPos.posToSectionCoord(transition.position().x());
        int destinationZ = SectionPos.posToSectionCoord(transition.position().z());
        arrivals.pending.begin(transports.holds(), originChunk.x(), originChunk.z(), destinationX, destinationZ, nodes, arrivals::place);
    }

    private void copyTree(Entity entity, TeleportTransition transition, int parentIndex, ServerLevel target, List<TeleportedNode> nodes) {
        List<Entity> passengers = entity.getPassengers();
        entity.ejectPassengers();
        Entity created = entity.getType().create(target, EntitySpawnReason.DIMENSION_TRAVEL);
        int myIndex = -1;
        if (created != null) {
            created.restoreFrom(entity);
            entity.removeAfterChangingDimensions();
            myIndex = nodes.size();
            nodes.add(new TeleportedNode(created, PositionMoveRotation.of(entity), transition, parentIndex));
        }

        for (Entity passenger : passengers) {
            copyTree(passenger, entity.calculatePassengerTransition(transition, passenger), myIndex, target, nodes);
        }
    }

    private void teleportSpectators(Entity entity, TeleportTransition transition) {
        for (ServerPlayer player : List.copyOf(level.players())) {
            if (player.getCamera() == entity) {
                DeferredWork.serial(DeferReason.PLAYER_TELEPORT, transports.stats(), () -> {
                    player.teleport(transition);
                    player.setCamera(null);
                })
                    .validIf(() -> !player.hasDisconnected() && !player.isRemoved() && player.level() == level)
                    .submit(transports);
            }
        }
    }

    /** The region task's hold has the target chunk loaded, so no sync load happens here. */
    private void place(List<TeleportedNode> nodes) {
        for (TeleportedNode node : nodes) {
            node.entity().teleportSetPosition(node.currentValues(), PositionMoveRotation.of(node.transition()), node.transition().relatives());
            level.addDuringTeleport(node.entity());
        }

        for (TeleportedNode node : nodes) {
            if (node.parentIndex() >= 0) {
                node.entity().startRiding(nodes.get(node.parentIndex()).entity(), true, false);
            }
        }

        level.resetEmptyTime();
        for (TeleportedNode node : nodes) {
            node.transition().postTeleportTransition().onTransition(node.entity());
        }
    }
}
