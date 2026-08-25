package fr.hardel.leafs.network;

import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.global.BarrierWindow;
import fr.hardel.leafs.metrics.DeferReason;
import fr.hardel.leafs.ticking.TickingManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

/**
 * The disconnect keeps the vanilla removal body whole, under the pause of every region, which makes
 * live cross-region state safe to touch. A thread that cannot raise that pause, a region worker or a
 * mod's own, hands the body to the window, which holds the same pause. The save serializes under it
 * and its disk writes leave on the deferred write thread, so the pause only ever covers in-memory work.
 */
public final class PlayerTeardown {

    private PlayerTeardown() {
    }

    public static void remove(PlayerList playerList, ServerPlayer player, Runnable vanillaRemove) {
        MinecraftServer server = playerList.getServer();
        if (!server.isSameThread()) {
            BarrierWindow.of(server).enqueue(DeferReason.PLAYER_TEARDOWN, vanillaRemove);
            return;
        }

        long start = System.nanoTime();
        TickingManager.of(server).pauseBatch().run(vanillaRemove);
        long millis = (System.nanoTime() - start) / 1_000_000L;
        if (millis > 50) {
            Leafs.LOGGER.warn("Teardown of {} took {} ms under the region pause", player.getPlainTextName(), millis);
        }
    }
}
