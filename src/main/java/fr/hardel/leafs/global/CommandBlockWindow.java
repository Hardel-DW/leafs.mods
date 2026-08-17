package fr.hardel.leafs.global;

import fr.hardel.leafs.metrics.WindowReason;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.MinecartCommandBlock;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Command blocks execute inside the barrier window because commands reach arbitrary state. */
public final class CommandBlockWindow {

    private CommandBlockWindow() {
    }

    /**
     * The block can change before the window runs, so its state is re-read there, and only if its
     * chunk is still loaded: the window must never trigger a synchronous load.
    * Skipped but rearmed exactly like vanilla's AUTO reschedule, so flipping the rule back on resumes every loop.
    */
    public static boolean deferBlockTick(ServerLevel level, BlockPos pos, BlockState state) {
        BlockPos target = pos.immutable();
        CommandBlockEntity repeating = level.getBlockEntity(target) instanceof CommandBlockEntity commandBlock
            && commandBlock.getMode() == CommandBlockEntity.Mode.AUTO ? commandBlock : null;

        if (repeating != null && !level.getGameRules().get(LeafsGameRules.repeatingCommandBlocksWork)) {
            if (repeating.isPowered() || repeating.isAutomatic()) {
                level.scheduleTick(target, state.getBlock(), 1);
            }

            return true;
        }

        boolean deferred = defer(level, WindowReason.COMMAND_BLOCK, () -> {
            if (!level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(target.getX()), SectionPos.blockToSectionCoord(target.getZ()))) {
                return;
            }

            BlockState current = level.getBlockState(target);
            if (current.getBlock() instanceof CommandBlock) {
                current.tick(level, target, level.getRandom());
            }
        });

        if (deferred && repeating != null) {
            ((GlobalServerAccess) level.getServer()).leafs$windowPressure().recordRepeatingDeferral();
        }

        return deferred;
    }

    /** The minecart can be destroyed before the window runs. */
    public static boolean deferMinecartActivation(ServerLevel level, MinecartCommandBlock minecart, int x, int y, int z, boolean powered) {
        return defer(level, WindowReason.MINECART_COMMAND_BLOCK, () -> {
            if (!minecart.isRemoved()) {
                minecart.activateMinecart(level, x, y, z, powered);
            }
        });
    }

    private static boolean defer(ServerLevel level, WindowReason reason, Runnable execution) {
        BarrierWindow window = BarrierWindow.of(level.getServer());
        if (window.isDraining()) {
            return false;
        }

        window.enqueue(reason, execution);

        return true;
    }
}
