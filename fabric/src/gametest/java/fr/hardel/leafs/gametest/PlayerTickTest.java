package fr.hardel.leafs.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.ChunkPos;

import java.util.UUID;

public final class PlayerTickTest {

    /** 2026-09-24: a player teleported to raw terrain was ticked by the server thread, which waited for the whole generation of the chunk. */
    @GameTest
    public void theServerThreadNeverTicksAPlayerOnAnUnloadedChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "leafs-gametest-player"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        ChunkPos origin = ChunkPos.containing(helper.absolutePos(BlockPos.ZERO));
        ChunkPos raw = new ChunkPos(origin.x() + 300, origin.z());

        player.setPos(raw.getMiddleBlockX(), 100, raw.getMiddleBlockZ());
        player.connection.tick();
        level.getServer().getPlayerList().remove(player);

        helper.assertFalse(level.hasChunk(raw.x(), raw.z()), "the tick of the player loaded %s".formatted(raw));
        helper.succeed();
    }
}
