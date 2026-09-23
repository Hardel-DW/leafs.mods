package fr.hardel.leafs.mixin.global;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.global.CommandEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(CommandBlock.class)
public abstract class CommandBlockMixin {

    @WrapMethod(method = "execute")
    private void leafs$runTheChainTogether(BlockState state, ServerLevel level, BlockPos pos, BaseCommandBlock commandBlock, boolean commandSet, Operation<Void> original) {
        CommandEngine.runCommandBlock(level, pos, () -> {
            original.call(state, level, pos, commandBlock, commandSet);
            return true;
        });
    }
}
