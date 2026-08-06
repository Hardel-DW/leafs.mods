package fr.hardel.leafs.global;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.MinecartCommandBlock;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Commands can reach arbitrary chunks, entities and dimensions, so command blocks execute as one
 * unmodified vanilla unit inside the barrier window (Compromise #4). A deferred unit re-enters the
 * same hook when the window replays it; {@link BarrierWindow#isDraining()} tells the hook the window's guarantees already hold.
 */
public final class CommandBlockWindow {

    private CommandBlockWindow() {
    }

    /**
     * The block can change before the window runs, so its state is re-read there, and only if its
     * chunk is still loaded: the window must never trigger a synchronous load.
     */
    public static boolean deferBlockTick(ServerLevel level, BlockPos pos, BlockState state) {
        BlockPos target = pos.immutable();
        CommandBlockEntity repeating = level.getBlockEntity(target) instanceof CommandBlockEntity commandBlock
            && commandBlock.getMode() == CommandBlockEntity.Mode.AUTO ? commandBlock : null;

        if (repeating != null && !level.getGameRules().get(LeafsGameRules.repeatingCommandBlocksWork)) {
            skipButKeepArmed(level, target, state, repeating);
            return true;
        }

        boolean deferred = defer(level, () -> {
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

    /**
     * The {@code leafs:repeating_command_blocks_work} cut: execution is skipped without the window,
     * but the reschedule mirrors vanilla's AUTO branch, so flipping the rule back on resumes every
     * loop without touching a block. Checked before the drain marker on purpose, a flip silences
     * even the units already queued.
     */
    private static void skipButKeepArmed(ServerLevel level, BlockPos pos, BlockState state, CommandBlockEntity commandBlock) {
        if (commandBlock.isPowered() || commandBlock.isAutomatic()) {
            level.scheduleTick(pos, state.getBlock(), 1);
        }
    }

    /** The minecart can be destroyed before the window runs. */
    public static boolean deferMinecartActivation(ServerLevel level, MinecartCommandBlock minecart, int x, int y, int z, boolean powered) {
        return defer(level, () -> {
            if (!minecart.isRemoved()) {
                minecart.activateMinecart(level, x, y, z, powered);
            }
        });
    }

    /** @return true when the caller must cancel because the execution was queued for this tick's window. */
    private static boolean defer(ServerLevel level, Runnable execution) {
        BarrierWindow window = ((GlobalServerAccess) level.getServer()).leafs$barrierWindow();
        if (window.isDraining()) {
            return false;
        }

        window.enqueue(execution);

        return true;
    }
}
