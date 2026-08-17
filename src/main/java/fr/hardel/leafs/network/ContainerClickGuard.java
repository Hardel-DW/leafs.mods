package fr.hardel.leafs.network;

import fr.hardel.leafs.Leafs;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Vanilla's click handler flips suppressRemoteUpdates around clicked() without a finally: one
 * exception and the menu never corrects the client again, a permanent inventory lie that only a
 * relog heals. A region thread can throw where vanilla's main thread could not, so the guard
 * restores the sync and sends a full resync before the error path runs.
 */
public final class ContainerClickGuard {

    private ContainerClickGuard() {
    }

    public static void handleGuarded(ServerPlayer player, ServerboundContainerClickPacket packet, Runnable original) {
        try {
            original.run();
        } catch (RuntimeException | Error exception) {
            if (!(exception instanceof RunningOnDifferentThreadException)) {
                AbstractContainerMenu menu = player.containerMenu;
                menu.resumeRemoteUpdates();
                menu.broadcastFullState();
                Leafs.LOGGER.warn("Container click of {} crashed on menu {} slot {} button {}; sync restored, full resync sent", player.getPlainTextName(), packet.containerId(), packet.slotNum(), packet.buttonNum(), exception);
            }

            throw exception;
        }
    }
}
