package fr.hardel.leafs.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.neoforge.BlockSnapshotCapture;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelChunk.class)
public abstract class LevelChunkSnapshotCaptureShim {

    @WrapOperation(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/Level;captureBlockSnapshots:Z", opcode = Opcodes.GETFIELD))
    private boolean leafs$capturingHere(Level level, Operation<Boolean> original) {
        return BlockSnapshotCapture.current().capturing() || original.call(level);
    }
}
