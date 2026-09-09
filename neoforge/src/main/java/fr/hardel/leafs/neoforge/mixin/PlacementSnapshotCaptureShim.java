package fr.hardel.leafs.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.neoforge.BlockSnapshotCapture;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;

/** NeoForge's item placement drives its capture through the level's fields; here it drives the thread's. */
@Mixin(CommonHooks.class)
public abstract class PlacementSnapshotCaptureShim {

    @WrapOperation(method = "onPlaceItemIntoWorld", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/Level;captureBlockSnapshots:Z", opcode = Opcodes.PUTFIELD))
    private static void leafs$captureHere(Level level, boolean capturing, Operation<Void> original) {
        BlockSnapshotCapture.current().capturing = capturing;
    }

    @WrapOperation(method = "onPlaceItemIntoWorld", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/Level;restoringBlockSnapshots:Z", opcode = Opcodes.PUTFIELD))
    private static void leafs$restoreHere(Level level, boolean restoring, Operation<Void> original) {
        BlockSnapshotCapture.current().restoring = restoring;
    }

    @WrapOperation(method = "onPlaceItemIntoWorld", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/Level;capturedBlockSnapshots:Ljava/util/ArrayList;", opcode = Opcodes.GETFIELD))
    private static ArrayList<BlockSnapshot> leafs$capturedHere(Level level, Operation<ArrayList<BlockSnapshot>> original) {
        return BlockSnapshotCapture.current().snapshots;
    }
}
