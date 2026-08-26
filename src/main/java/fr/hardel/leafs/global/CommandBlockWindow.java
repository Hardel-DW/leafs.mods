package fr.hardel.leafs.global;

import fr.hardel.leafs.metrics.DeferReason;
import fr.hardel.leafs.scheduler.DeferredTransports;
import fr.hardel.leafs.scheduler.DeferredWork;
import fr.hardel.leafs.ticking.TickingBinding;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.MinecartCommandBlock;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Command blocks execute inside the sync window because commands reach arbitrary state. */
public final class CommandBlockWindow {

    private CommandBlockWindow() {
    }

    /** The state re-reads inside the window, only if the chunk is still loaded. A block cut by the gamerule rearms like vanilla's AUTO reschedule. */
    public static boolean deferBlockTick(ServerLevel level, BlockPos pos, BlockState state) {
        DeferredTransports transports = TickingBinding.of(level);
        if (transports.holdsWindow()) {
            return false;
        }

        BlockPos target = pos.immutable();
        CommandBlockEntity repeating = level.getBlockEntity(target) instanceof CommandBlockEntity commandBlock && commandBlock.getMode() == CommandBlockEntity.Mode.AUTO ? commandBlock : null;

        if (repeating != null && !level.getGameRules().get(LeafsGameRules.repeatingCommandBlocksWork)) {
            if (repeating.isPowered() || repeating.isAutomatic()) {
                level.scheduleTick(target, state.getBlock(), 1);
            }

            return true;
        }

        DeferReason reason = repeating != null ? DeferReason.REPEATING_COMMAND_BLOCK : DeferReason.COMMAND_BLOCK;
        return DeferredWork.window(reason, transports.stats(), () -> tickCommandBlock(level, target))
            .validIf(() -> level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(target.getX()), SectionPos.blockToSectionCoord(target.getZ())))
            .submit(transports);
    }

    /** The minecart can be destroyed before the window runs. Same re-entry rule as the block: the window's replay runs in place. */
    public static boolean deferMinecartActivation(ServerLevel level, MinecartCommandBlock minecart, int x, int y, int z, boolean powered) {
        DeferredTransports transports = TickingBinding.of(level);
        if (transports.holdsWindow()) {
            return false;
        }

        return DeferredWork.window(DeferReason.MINECART_COMMAND_BLOCK, transports.stats(), () -> minecart.activateMinecart(level, x, y, z, powered))
            .validIf(() -> !minecart.isRemoved())
            .submit(transports);
    }

    private static void tickCommandBlock(ServerLevel level, BlockPos target) {
        BlockState current = level.getBlockState(target);
        if (current.getBlock() instanceof CommandBlock) {
            current.tick(level, target, level.getRandom());
        }
    }
}
