package fr.hardel.leafs.debug;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.hardel.leafs.chunk.LevelChunks;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.TickingBlockEntity;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

public final class LagCommand {
    private static final Queue<LagSource> sources = new ConcurrentLinkedQueue<>();

    private LagCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("lag")
            .then(Commands.literal("clear").executes(context -> clear(context.getSource())))
            .then(Commands.argument("ms", IntegerArgumentType.integer(1, 1000))
                .executes(context -> add(context.getSource(), BlockPos.containing(context.getSource().getPosition()), IntegerArgumentType.getInteger(context, "ms")))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(context -> add(context.getSource(), BlockPosArgument.getBlockPos(context, "pos"), IntegerArgumentType.getInteger(context, "ms")))));
    }

    private static int add(CommandSourceStack source, BlockPos pos, int millis) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        if (!level.hasChunkAt(pos)) {
            throw BlockPosArgument.ERROR_NOT_LOADED.create();
        }

        LagSource lag = new LagSource(pos, TimeUnit.MILLISECONDS.toNanos(millis));
        sources.add(lag);
        LevelChunks.of(level).owners().route(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()), () -> level.addBlockEntityTicker(lag));
        source.sendSuccess(() -> Component.literal("The region holding %s now spends %s ms more per tick, /leafs lag clear removes it".formatted(pos.toShortString(), millis))
            .withStyle(ChatFormatting.RED), true);
        return millis;
    }

    private static int clear(CommandSourceStack source) {
        int cleared = 0;
        for (LagSource lag = sources.poll(); lag != null; lag = sources.poll()) {
            lag.removed = true;
            cleared++;
        }

        int count = cleared;
        source.sendSuccess(() -> Component.literal("Removed %s lag sources".formatted(count)).withStyle(ChatFormatting.GREEN), true);
        return count;
    }

    private static final class LagSource implements TickingBlockEntity {
        private final BlockPos pos;
        private final long nanos;
        private volatile boolean removed;

        private LagSource(BlockPos pos, long nanos) {
            this.pos = pos;
            this.nanos = nanos;
        }

        @Override
        public void tick() {
            long end = System.nanoTime() + nanos;
            while (System.nanoTime() < end) {
                Thread.onSpinWait();
            }
        }

        @Override
        public boolean isRemoved() {
            return removed;
        }

        @Override
        public BlockPos getPos() {
            return pos;
        }

        @Override
        public String getType() {
            return "leafs:lag";
        }
    }
}
