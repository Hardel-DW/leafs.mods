package fr.hardel.leafs.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;

public final class ChunkPacketTest {

    @GameTest
    public void aSectionWrittenBetweenItsMeasureAndItsWriteStillSerializes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PalettedContainerFactory factory = level.palettedContainerFactory();
        LevelChunk chunk = new LevelChunk(level, new ChunkPos(0, 0));
        chunk.getSections()[0] = new LevelChunkSection(new GrowsAfterItsMeasure(factory), factory.createForBiomes());

        FriendlyByteBuf sent = new ClientboundLevelChunkPacketData(chunk).getReadBuffer();
        for (LevelChunkSection section : chunk.getSections()) {
            LevelChunkSection received = new LevelChunkSection(factory);
            received.read(sent);
            helper.assertTrue(sameBlocks(section, received), "the client receives the blocks of the section");
        }

        helper.assertTrue(sent.readableBytes() == 0, "the packet holds exactly the sections");
        helper.succeed();
    }

    private static boolean sameBlocks(LevelChunkSection expected, LevelChunkSection actual) {
        for (int index = 0; index < 4096; index++) {
            if (expected.getBlockState(index & 15, index >> 8, index >> 4 & 15) != actual.getBlockState(index & 15, index >> 8, index >> 4 & 15)) {
                return false;
            }
        }

        return true;
    }

    private static final class GrowsAfterItsMeasure extends PalettedContainer<BlockState> {
        private GrowsAfterItsMeasure(PalettedContainerFactory factory) {
            super(factory.defaultBlockState(), factory.blockStatesStrategy());
        }

        @Override
        public int getSerializedSize() {
            int measured = super.getSerializedSize();
            set(0, 0, 0, Blocks.STONE.defaultBlockState());
            return measured;
        }
    }
}
