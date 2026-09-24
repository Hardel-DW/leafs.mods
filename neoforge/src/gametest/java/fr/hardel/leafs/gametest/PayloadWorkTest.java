package fr.hardel.leafs.gametest;

import fr.hardel.leafs.network.PacketRouting;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.ServerPayloadContext;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PayloadWorkTest {

    /** 2026-09-25: the work of a mod payload ran on the server thread while a region ticked its player. */
    public static void modWorkRunsOnThePlayerQueue(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(helper.absoluteVec(Vec3.ZERO));
        ServerPayloadContext context = new ServerPayloadContext(player.connection, Identifier.fromNamespaceAndPath(LeafsGameTests.MOD_ID, "payload"));
        AtomicBoolean onThePlayerQueue = new AtomicBoolean();

        CompletableFuture.runAsync(() -> context.enqueueWork(() -> onThePlayerQueue.set(PacketRouting.handledByCurrentDrain(player.connection))));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(onThePlayerQueue.get(), "the payload work did not run on the queue of the player"))
                .thenExecute(() -> helper.getLevel().getServer().getPlayerList().remove(player))
                .thenSucceed();
    }
}
