package fr.hardel.leafs.entity;

import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.owner.DeferredWork;
import fr.hardel.leafs.metrics.DeferReason;
import fr.hardel.leafs.ticking.RegionBorrow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PortalProcessor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.portal.TeleportTransition;

public final class EntityTeleports {
    private final ServerLevel level;

    public EntityTeleports(ServerLevel level) {
        this.level = level;
    }

    public boolean route(Entity entity, TeleportTransition transition) {
        RegionBorrow.atContact(entity);
        ChunkPos origin = entity.chunkPosition();
        if (LevelChunks.of(level).owners().holds(origin.x(), origin.z())) {
            return false;
        }

        DeferReason reason = entity instanceof ServerPlayer ? DeferReason.PLAYER_TELEPORT : DeferReason.TELEPORT;
        DeferredWork.owner(level, reason, origin.x(), origin.z(), () -> entity.teleport(transition))
            .validIf(() -> stillTeleportable(entity))
            .submit();

        return true;
    }

    public void deferPortal(Entity entity, PortalProcessor process) {
        ChunkPos chunk = entity.chunkPosition();
        DeferredWork.owner(level, DeferReason.PORTAL, chunk.x(), chunk.z(), () -> searchAndEnterPortal(entity, process))
            .validIf(() -> stillTeleportable(entity))
            .submit();
    }

    private void searchAndEnterPortal(Entity entity, PortalProcessor process) {
        TeleportTransition transition = process.getPortalDestination(level, entity);
        if (transition != null) {
            entity.teleportToPortalDestination(level, transition);
        }
    }

    private boolean stillTeleportable(Entity entity) {
        if (entity instanceof ServerPlayer player && player.hasDisconnected()) {
            return false;
        }

        return !entity.isRemoved() && entity.level() == level;
    }
}
