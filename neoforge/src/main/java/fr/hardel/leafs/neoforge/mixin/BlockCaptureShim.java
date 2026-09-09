package fr.hardel.leafs.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.neoforge.BlockSnapshotCapture;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/** NeoForge captures a broken block's drops in one static list meant for the server thread alone; regions break blocks in parallel, so each thread captures its own. */
@Mixin(Block.class)
public abstract class BlockCaptureShim {
    @Unique
    private static final ThreadLocal<List<ItemEntity>> leafs$capturedDrops = new ThreadLocal<>();

    @WrapOperation(method = {"beginCapturingDrops", "stopCapturingDrops", "popResource(Lnet/minecraft/world/level/Level;Ljava/util/function/Supplier;Lnet/minecraft/world/item/ItemStack;)V"},
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/block/Block;capturedDrops:Ljava/util/List;", opcode = Opcodes.GETSTATIC))
    private static List<ItemEntity> leafs$capturedOnThisThread(Operation<List<ItemEntity>> original) {
        return leafs$capturedDrops.get();
    }

    @WrapOperation(method = {"beginCapturingDrops", "stopCapturingDrops"},
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/block/Block;capturedDrops:Ljava/util/List;", opcode = Opcodes.PUTSTATIC))
    private static void leafs$captureOnThisThread(List<ItemEntity> drops, Operation<Void> original) {
        leafs$capturedDrops.set(drops);
    }

    /** No drops while a placement restores its snapshots: the thread's restore, or a mod driving the level's flag. */
    @WrapOperation(method = "popResource(Lnet/minecraft/world/level/Level;Ljava/util/function/Supplier;Lnet/minecraft/world/item/ItemStack;)V",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/Level;restoringBlockSnapshots:Z", opcode = Opcodes.GETFIELD))
    private static boolean leafs$restoringHere(Level level, Operation<Boolean> original) {
        return BlockSnapshotCapture.current().restoring || original.call(level);
    }
}
