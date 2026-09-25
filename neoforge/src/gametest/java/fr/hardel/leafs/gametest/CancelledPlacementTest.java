package fr.hardel.leafs.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.concurrent.atomic.AtomicBoolean;

public final class CancelledPlacementTest {
    private static final BlockPos TNT = BlockPos.ZERO;
    private static final BlockPos REDSTONE = TNT.east();

    /** 2026-09-25: a TNT placed next to a redstone block was primed during the capture, before a mod cancelled the placement. */
    public static void aCancelledPlacementPrimesNoTnt(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(helper.absoluteVec(new Vec3(0.5, 2, 0.5)));
        AtomicBoolean cancelled = new AtomicBoolean();
        NeoForge.EVENT_BUS.addListener((BlockEvent.EntityPlaceEvent event) -> {
            if (event.getEntity() == player) {
                event.setCanceled(true);
                cancelled.set(true);
            }
        });
        
        helper.setBlock(REDSTONE, Blocks.REDSTONE_BLOCK);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TNT));
        
        helper.placeAt(player, player.getMainHandItem(), REDSTONE, Direction.WEST);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> helper.assertTrue(cancelled.get(), "the placement never reached the place event"))
                .thenExecute(() -> helper.assertEntityNotPresent(EntityType.TNT, new AABB(TNT).inflate(1)))
                .thenExecute(() -> helper.assertBlockPresent(Blocks.AIR, TNT))
                .thenExecute(() -> helper.getLevel().getServer().getPlayerList().remove(player))
                .thenSucceed();
    }
}
