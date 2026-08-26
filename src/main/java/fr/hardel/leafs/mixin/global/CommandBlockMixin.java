package fr.hardel.leafs.mixin.global;

import fr.hardel.leafs.global.CommandBlockWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hook only, global/CommandBlockWindow moves the whole scheduled tick into the barrier window. */
@Mixin(CommandBlock.class)
public abstract class CommandBlockMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void leafs$deferToBarrierWindow(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo callbackInfo) {
        if (CommandBlockWindow.deferBlockTick(level, pos, state)) {
            callbackInfo.cancel();
        }
    }
}
