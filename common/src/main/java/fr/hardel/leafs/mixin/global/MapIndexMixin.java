package fr.hardel.leafs.mixin.global;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.global.SharedStateMonitor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapIndex;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(MapIndex.class)
public abstract class MapIndexMixin {

    @WrapMethod(method = "getNextMapId")
    private MapId leafs$lockedGetNextMapId(Operation<MapId> original) {
        return SharedStateMonitor.call(this, original::call);
    }
}
