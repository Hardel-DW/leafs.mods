package fr.hardel.leafs.entity;

import fr.hardel.leafs.chunk.DegradedChunkReads;
import fr.hardel.leafs.ownership.OwnershipViolationException;
import fr.hardel.leafs.scheduler.SharedChunkHolds;
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
import java.util.function.Function;

/** Routes teleports a region worker may not run in place: serial for same-level out-of-region, pipeline for cross-dimension. */
public final class EntityTeleports {

    /** Wiring to the ticking surfaces of this level, provided at construction so this module stays free of ticking/ types. */
    public interface LevelBinding {

        SharedChunkHolds holds();

        boolean currentRegionOwns(int chunkX, int chunkZ);

        void submitSerial(Runnable task);

        /** The barrier window: all regions paused, full world access, for work whose reach is not known in advance. */
        void submitWindow(Runnable task);

        void submitPlacement(int chunkX, int chunkZ, Runnable placement);
    }

    private record TeleportedNode(Entity entity, PositionMoveRotation currentValues, TeleportTransition transition, int parentIndex) {
    }

    private final ServerLevel level;
    private final LevelBinding binding;
    private final Function<ServerLevel, EntityTeleports> byLevel;
    private final PendingTeleports<List<TeleportedNode>> pending;

    public EntityTeleports(ServerLevel level, LevelBinding binding, Function<ServerLevel, EntityTeleports> byLevel) {
        this.level = level;
        this.binding = binding;
        this.byLevel = byLevel;
        this.pending = new PendingTeleports<>(binding::submitPlacement);
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
        if (target == level && binding.currentRegionOwns(destinationX, destinationZ)) {
            return false;
        }

        if (target == level) {
            binding.submitSerial(() -> {
                if (!entity.isRemoved() && entity.level() == level) {
                    entity.teleport(transition);
                }
            });

            return true;
        }

        beginCrossDimension(entity, transition);

        return true;
    }

    /** Defers to serial unless the current region owns both the player and the destination. */
    public boolean divertPlayerFromRegion(ServerPlayer player, TeleportTransition transition) {
        if (transition.newLevel() == level
            && binding.currentRegionOwns(player.chunkPosition().x(), player.chunkPosition().z())
            && binding.currentRegionOwns(SectionPos.posToSectionCoord(transition.position().x()), SectionPos.posToSectionCoord(transition.position().z()))) {
            return false;
        }

        binding.submitSerial(() -> {
            if (!player.hasDisconnected() && !player.isRemoved() && player.level() == level) {
                player.teleport(transition);
            }
        });

        return true;
    }

    /**
     * Runs in the barrier window because the search writes blocks in an unknown dimension, but the
     * window never generates: the search runs in degraded reads, an absent chunk files a demand
     * ticket and aborts the attempt, and the retry finds the chunk once the pool generated it. Past
     * the retry budget, one vanilla attempt loads synchronously under the window as a last resort.
     */
    public void deferPortal(Entity entity) {
        deferPortal(entity, 0);
    }

    private static final int PORTAL_SEARCH_WINDOW_BUDGET = 100;

    private void deferPortal(Entity entity, int attempts) {
        binding.submitWindow(() -> {
            if (entity.isRemoved() || entity.level() != level) {
                return;
            }

            PortalProcessor process = entity.portalProcess;
            if (process == null) {
                return;
            }

            TeleportTransition transition;
            if (attempts < PORTAL_SEARCH_WINDOW_BUDGET) {
                try {
                    transition = DegradedChunkReads.call(() -> process.getPortalDestination(level, entity));
                } catch (OwnershipViolationException absentChunk) {
                    deferPortal(entity, attempts + 1);
                    return;
                }
            } else {
                transition = process.getPortalDestination(level, entity);
            }

            if (transition == null) {
                return;
            }

            ServerLevel target = transition.newLevel();
            if (level.isAllowedToEnterPortal(target) && (target.dimension() == level.dimension() || entity.canTeleport(level, target))) {
                entity.teleport(transition);
            }
        });
    }

    private void beginCrossDimension(Entity entity, TeleportTransition transition) {
        ServerLevel target = transition.newLevel();
        List<TeleportedNode> nodes = new ArrayList<>();
        copyTree(entity, transition, -1, target, nodes);
        teleportSpectators(entity, transition);
        if (nodes.isEmpty()) {
            return;
        }

        EntityTeleports arrivals = byLevel.apply(target);
        ChunkPos originChunk = entity.chunkPosition();
        int destinationX = SectionPos.posToSectionCoord(transition.position().x());
        int destinationZ = SectionPos.posToSectionCoord(transition.position().z());
        arrivals.pending.begin(binding.holds(), originChunk.x(), originChunk.z(), destinationX, destinationZ, nodes, arrivals::place);
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
                binding.submitSerial(() -> {
                    if (!player.hasDisconnected() && !player.isRemoved() && player.level() == level) {
                        player.teleport(transition);
                        player.setCamera(null);
                    }
                });
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
