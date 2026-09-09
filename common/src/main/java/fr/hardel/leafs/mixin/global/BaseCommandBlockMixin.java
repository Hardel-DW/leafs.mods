package fr.hardel.leafs.mixin.global;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.global.CommandEngine;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BaseCommandBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Hook only, one funnel for the block and the minecart: the whole run, success count included, goes through global/CommandEngine. */
@Mixin(BaseCommandBlock.class)
public abstract class BaseCommandBlockMixin {

    @Shadow
    public abstract CommandSourceStack createCommandSourceStack(ServerLevel level, CommandSource source);

    @WrapMethod(method = "performCommand")
    private boolean leafs$runThroughTheEngine(ServerLevel level, Operation<Boolean> original) {
        return CommandEngine.runCommandBlock(level, createCommandSourceStack(level, CommandSource.NULL).getPosition(), () -> original.call(level));
    }
}
