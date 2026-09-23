package fr.hardel.leafs.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.SynchronizedObject2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = {
    // LootrMinecraft/Lootr, block entity entries, registered by chunk loads from any thread and walked and removed by the server tick.
    "noobanidus.mods.lootr.common.block.entity.BlockEntityTicker"
})
public abstract class ConstructorObject2ObjectMapMixin {

    @WrapOperation(method = "<init>", at = @At(value = "NEW", target = "it/unimi/dsi/fastutil/objects/Object2ObjectOpenHashMap"))
    private static <K, V> Object2ObjectOpenHashMap<K, V> leafs$synchronized(Operation<Object2ObjectOpenHashMap<K, V>> original) {
        return new SynchronizedObject2ObjectOpenHashMap<>();
    }
}
