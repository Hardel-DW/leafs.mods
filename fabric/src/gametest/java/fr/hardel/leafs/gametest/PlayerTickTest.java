package fr.hardel.leafs.gametest;

import com.mojang.authlib.GameProfile;
import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.pool.ChunkPool;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

public final class PlayerTickTest {
    private static final double RAW_Y = 100;
    private static final double STEP_BLOCKS = 4;
    private static final int STEPS = 10;
    private static final int WAITED_TICKS = 40;
    private static final long BUSY_NANOS = TimeUnit.SECONDS.toNanos(3);

    /** 2026-09-24: a player teleported to raw terrain was ticked by the server thread, which waited for the whole generation of the chunk. */
    @GameTest
    public void theServerThreadNeverTicksAPlayerOnAnUnloadedChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = joined(helper, Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)));
        ChunkPos raw = rawChunk(helper, 300);

        player.setPos(onRawTerrain(raw));
        player.connection.tick();
        level.getServer().getPlayerList().remove(player);

        helper.assertFalse(level.hasChunk(raw.x(), raw.z()), "the tick of the player loaded %s".formatted(raw));
        helper.succeed();
    }

    @GameTest
    public void aPlayerAnsweringItsJoinBeforeItsFirstTickIsNotSentBack(GameTestHelper helper) {
        Vec3 spawn = onRawTerrain(rawChunk(helper, 600));
        ServerPlayer player = joined(helper, spawn);

        accept(player, 1, spawn);
        Vec3 moved = moveOnce(player, spawn);
        helper.getLevel().getServer().getPlayerList().remove(player);

        helper.assertValueEqual(player.position(), moved, "the position the player moved to");
        helper.succeed();
    }

    @GameTest
    public void aPlayerWaitingForItsChunksKeepsItsMoves(GameTestHelper helper) {
        ServerPlayer player = joined(helper, Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)));
        player.connection.tick();
        accept(player, 1, player.position());
        Vec3 position = onRawTerrain(rawChunk(helper, 900));

        player.connection.teleport(position.x, position.y, position.z, 0, 0);
        player.connection.resetPosition();
        player.connection.tick();
        accept(player, 2, position);
        for (int step = 0; step < STEPS; step++) {
            position = moveOnce(player, position);
            player.connection.tick();
        }

        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertValueEqual(player.position(), position, "the position the player flew to");
        helper.succeed();
    }

    /** 2026-10-06: a bot flying ahead of the generation stood where its region listed no chunk yet, and the region left it out of its ticks. */
    @GameTest(maxTicks = 200)
    public void aPlayerAheadOfTheGenerationIsTicked(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkPool pool = LevelChunks.of(level).pool();
        ServerPlayer player = joined(helper, Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)));
        Vec3 landed = onRawTerrain(rawChunk(helper, 1200));
        AtomicInteger ticksBefore = new AtomicInteger();

        helper.startSequence()
            .thenExecute(() -> {
                player.connection.tick();
                accept(player, 1, player.position());
                for (int worker = 0; worker < pool.threads(); worker++) {
                    pool.execute(() -> LockSupport.parkNanos(BUSY_NANOS));
                }

                player.connection.teleport(landed.x, landed.y, landed.z, 0, 0);
                player.connection.resetPosition();
                accept(player, 2, landed);
                ticksBefore.set(player.tickCount);
            })
            .thenIdle(WAITED_TICKS)
            .thenExecute(() -> {
                int ticked = player.tickCount - ticksBefore.get();
                level.getServer().getPlayerList().remove(player);
                helper.assertTrue(ticked >= WAITED_TICKS / 2, "the player was ticked %s times in %s ticks".formatted(ticked, WAITED_TICKS));
            })
            .thenSucceed();
    }

    private static ServerPlayer joined(GameTestHelper helper, Vec3 position) {
        ServerLevel level = helper.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "leafs-gametest-player"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        player.setPos(position);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());

        return player;
    }

    private static ChunkPos rawChunk(GameTestHelper helper, int distance) {
        ChunkPos origin = ChunkPos.containing(helper.absolutePos(BlockPos.ZERO));
        return new ChunkPos(origin.x() + distance, origin.z());
    }

    private static Vec3 onRawTerrain(ChunkPos raw) {
        return new Vec3(raw.getMiddleBlockX(), RAW_Y, raw.getMiddleBlockZ());
    }

    private static void accept(ServerPlayer player, int teleport, Vec3 position) {
        player.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(teleport, position.x, position.y, position.z, 0, 0));
    }

    private static Vec3 moveOnce(ServerPlayer player, Vec3 from) {
        Vec3 to = from.add(STEP_BLOCKS, 0, 0);
        player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(to, false, false));
        player.connection.handleClientTickEnd(ServerboundClientTickEndPacket.INSTANCE);

        return to;
    }
}
