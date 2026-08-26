package fr.hardel.leafs.mixin.world;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Map;

// Vanilla confines these caches to the server thread; regions search too, so every entry takes the instance monitor.
@Mixin(StructureCheck.class)
public abstract class StructureCheckMixin {

    @WrapMethod(method = "checkStart")
    private StructureCheckResult leafs$monitoredCheck(ChunkPos pos, Structure structure, StructurePlacement placement, boolean requireUnreferenced, Operation<StructureCheckResult> original) {
        synchronized (this) {
            return original.call(pos, structure, placement, requireUnreferenced);
        }
    }

    @WrapMethod(method = "onStructureLoad")
    private void leafs$monitoredLoad(ChunkPos pos, Map<Structure, StructureStart> starts, Operation<Void> original) {
        synchronized (this) {
            original.call(pos, starts);
        }
    }

    @WrapMethod(method = "incrementReference")
    private void leafs$monitoredReference(ChunkPos pos, Structure structure, Operation<Void> original) {
        synchronized (this) {
            original.call(pos, structure);
        }
    }
}
