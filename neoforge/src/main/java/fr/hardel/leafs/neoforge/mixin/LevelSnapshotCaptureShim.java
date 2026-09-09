package fr.hardel.leafs.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.neoforge.BlockSnapshotCapture;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;

/** A block set while this thread captures lands in the thread's capture; a mod driving the level's own flag keeps the level's list, as before. */
@Mixin(Level.class)
public abstract class LevelSnapshotCaptureShim {

    @WrapOperation(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/Level;captureBlockSnapshots:Z", opcode = Opcodes.GETFIELD))
    private boolean leafs$capturingHere(Level level, Operation<Boolean> original) {
        return BlockSnapshotCapture.current().capturing || original.call(level);
    }

    @WrapOperation(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/Level;capturedBlockSnapshots:Ljava/util/ArrayList;", opcode = Opcodes.GETFIELD))
    private ArrayList<BlockSnapshot> leafs$capturedHere(Level level, Operation<ArrayList<BlockSnapshot>> original) {
        BlockSnapshotCapture capture = BlockSnapshotCapture.current();
        return capture.capturing ? capture.snapshots : original.call(level);
    }
}
