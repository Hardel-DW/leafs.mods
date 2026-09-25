package fr.hardel.leafs.debug;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.hardel.leafs.chunk.owner.Work;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.RegionTickData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

public final class CrashCommand {

    private CrashCommand() {}

    static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("crash")
            .then(Commands.argument("dimension", DimensionArgument.dimension())
                .then(Commands.argument("region", IntegerArgumentType.integer(0))
                    .executes(context -> crash(context.getSource(), DimensionArgument.getDimension(context, "dimension"), IntegerArgumentType.getInteger(context, "region")))));
    }

    private static int crash(CommandSourceStack source, ServerLevel level, int regionId) {
        LevelRegions regions = LevelRegions.of(level);
        for (Region<RegionTickData> region : regions.regionizer().regionsView()) {
            if (region.id() == regionId && region.data().handle() != null && !region.data().handle().isCancelled()) {
                long section = region.sectionKeySnapshot()[0];
                int shift = regions.regionizer().sectionShift();
                region.data().inbox().post(ChunkPos.getX(section) << shift, ChunkPos.getZ(section) << shift, Work.GAME, () -> {
                    throw new IllegalStateException("Crash requested by /leafs crash on region #%s".formatted(regionId));
                });
                source.sendSuccess(() -> Component.literal("Region #%s will throw on its next tick".formatted(regionId)).withStyle(ChatFormatting.RED), true);
                return 1;
            }
        }

        source.sendFailure(Component.literal("No live region #%s in this dimension, /leafs regions lists them".formatted(regionId)));
        return 0;
    }
}
