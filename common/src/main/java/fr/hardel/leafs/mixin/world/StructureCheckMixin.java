package fr.hardel.leafs.mixin.world;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.chunk.StructureChecks;
import fr.hardel.leafs.global.SharedStateMonitor;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;

/** Vanilla confines these caches to the server thread; regions and chunk workers search too, so the two tables take the instance monitor, never the disk read, see {@link StructureChecks}. */
@Mixin(StructureCheck.class)
public abstract class StructureCheckMixin {

    @WrapMethod(method = "checkStart")
    private StructureCheckResult leafs$checkOffTheMonitor(ChunkPos pos, Structure structure, StructurePlacement placement, boolean requireUnreferenced, Operation<StructureCheckResult> original) {
        return StructureChecks.check((StructureCheck) (Object) this, pos, structure, placement, requireUnreferenced);
    }

    /** A disk scan stores under the monitor and only where nothing landed since: a chunk loaded meanwhile is fresher than its file. */
    @WrapOperation(method = "tryLoadFromStorage", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/structure/StructureCheck;storeFullResults(JLit/unimi/dsi/fastutil/objects/Object2IntMap;)V"))
    private void leafs$storeUnlessFresher(StructureCheck self, long key, Object2IntMap<Structure> starts, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> {
            if (!self.loadedChunks.containsKey(key)) {
                original.call(self, key, starts);
            }
        });
    }

    @WrapMethod(method = "onStructureLoad")
    private void leafs$monitoredLoad(ChunkPos pos, Map<Structure, StructureStart> starts, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(pos, starts));
    }

    @WrapMethod(method = "incrementReference")
    private void leafs$monitoredReference(ChunkPos pos, Structure structure, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(pos, structure));
    }
}
