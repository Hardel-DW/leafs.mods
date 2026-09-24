package fr.hardel.leafs.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class FreshBlockEntityTest {
    static final Identifier LIFECYCLE = Identifier.fromNamespaceAndPath(LeafsGameTests.MOD_ID, "lifecycle");

    /** 2026-09-25: a block entity of a FULL chunk that never ticks never ran onLoad, and received onChunkUnloaded alone. */
    public static void aBlockEntityOfAFullChunkLoadsBeforeItUnloads(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkPos origin = ChunkPos.containing(helper.absolutePos(BlockPos.ZERO));
        ChunkPos far = new ChunkPos(origin.x() + 64, origin.z());
        BlockPos pos = far.getBlockAt(8, 80, 8);
        level.getChunkSource().addTicketWithRadius(TicketType.PLAYER_LOADING, far, 0);
        level.setBlock(pos, BuiltInRegistries.BLOCK.getValue(LIFECYCLE).defaultBlockState(), Block.UPDATE_ALL);
        LifecycleEntity entity = (LifecycleEntity) level.getBlockEntity(pos);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(entity.calls.contains("onLoad"), "the block entity of the FULL chunk %s never ran onLoad".formatted(far)))
                .thenExecute(() -> level.getChunkSource().removeTicketWithRadius(TicketType.PLAYER_LOADING, far, 0))
                .thenWaitUntil(() -> helper.assertTrue(entity.calls.equals(List.of("onLoad", "onChunkUnloaded")), "the block entity saw %s".formatted(entity.calls)))
                .thenSucceed();
    }

    static final class LifecycleBlock extends Block implements EntityBlock {
        LifecycleBlock() {
            super(BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, LIFECYCLE)));
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new LifecycleEntity(pos, state);
        }
    }

    static final class LifecycleEntity extends BlockEntity {
        private final List<String> calls = new CopyOnWriteArrayList<>();

        LifecycleEntity(BlockPos pos, BlockState state) {
            super(BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(LIFECYCLE), pos, state);
        }

        @Override
        public void onLoad() {
            calls.add("onLoad");
        }

        @Override
        public void onChunkUnloaded() {
            calls.add("onChunkUnloaded");
        }
    }
}
