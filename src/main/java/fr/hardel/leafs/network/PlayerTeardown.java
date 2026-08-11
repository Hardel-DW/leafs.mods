package fr.hardel.leafs.network;

import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.ticking.LeafsServerAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

/**
 * The disconnect keeps the vanilla removal body whole, under the pause of every region, which makes
 * live cross-region state safe to touch. The save serializes under the pause and its disk writes
 * leave on the deferred write thread, so the pause only ever covers in-memory work.
 */
public final class PlayerTeardown {

    private PlayerTeardown() {
    }

    public static void remove(PlayerList playerList, ServerPlayer player, Runnable vanillaRemove) {
        MinecraftServer server = playerList.getServer();
        if (!server.isSameThread()) {
            vanillaRemove.run();
            return;
        }

        long start = System.nanoTime();
        ((LeafsServerAccess) server).leafs$ticking().runWithRegionsPaused(vanillaRemove);
        long millis = (System.nanoTime() - start) / 1_000_000L;
        if (millis > 50) {
            Leafs.LOGGER.warn("Teardown of {} took {} ms under the region pause", player.getPlainTextName(), millis);
        }
    }
}
