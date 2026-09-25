package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.owner.Work;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @WrapOperation(method = {"lambda$updatePOIOnBlockStateChange$0", "lambda$updatePOIOnBlockStateChange$2"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;execute(Ljava/lang/Runnable;)V"))
    private void leafs$poiWriteOnTheOwner(MinecraftServer server, Runnable write, Operation<Void> original, @Local(argsOnly = true) BlockPos pos) {
        LevelChunks.of((ServerLevel) (Object) this).owners().submit(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()), Work.GAME, write);
    }
}
