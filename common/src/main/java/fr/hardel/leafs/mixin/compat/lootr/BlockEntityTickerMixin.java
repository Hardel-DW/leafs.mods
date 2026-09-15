package fr.hardel.leafs.mixin.compat.lootr;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.SynchronizedObject2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** LootrMinecraft/Lootr, block entity ticker. Chunk loads register containers from any thread; the server tick walks the entries and removes. */
@Pseudo
@Mixin(targets = "noobanidus.mods.lootr.common.block.entity.BlockEntityTicker")
public abstract class BlockEntityTickerMixin {

    @WrapOperation(method = "<clinit>", at = @At(value = "NEW", target = "it/unimi/dsi/fastutil/objects/Object2ObjectOpenHashMap"))
    private static <K, V> Object2ObjectOpenHashMap<K, V> leafs$sharedTickers(Operation<Object2ObjectOpenHashMap<K, V>> original) {
        return new SynchronizedObject2ObjectOpenHashMap<>();
    }

    @WrapOperation(method = "<init>", at = @At(value = "NEW", target = "it/unimi/dsi/fastutil/objects/Object2ObjectOpenHashMap"))
    private static <K, V> Object2ObjectOpenHashMap<K, V> leafs$sharedEntries(Operation<Object2ObjectOpenHashMap<K, V>> original) {
        return new SynchronizedObject2ObjectOpenHashMap<>();
    }
}
