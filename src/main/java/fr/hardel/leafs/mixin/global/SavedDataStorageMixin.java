package fr.hardel.leafs.mixin.global;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.global.SharedStateMonitor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Map;

/**
 * The #26 lock: the cache's read-through population and the dirty sweep serialize on the storage,
 * and each encode serializes on its data instance - the same monitor the region-side mutators of
 * that instance take (#26b/#26c) - so a save never encodes state a region is mid-writing.
 */
@Mixin(SavedDataStorage.class)
public abstract class SavedDataStorageMixin {

    @WrapMethod(method = "computeIfAbsent")
    private <T extends SavedData> T leafs$lockedComputeIfAbsent(SavedDataType<T> type, Operation<T> original) {
        return SharedStateMonitor.call(this, () -> original.call(type));
    }

    @WrapMethod(method = "get")
    private <T extends SavedData> T leafs$lockedGet(SavedDataType<T> type, Operation<T> original) {
        return SharedStateMonitor.call(this, () -> original.call(type));
    }

    @WrapMethod(method = "set")
    private <T extends SavedData> void leafs$lockedSet(SavedDataType<T> type, T data, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(type, data));
    }

    @WrapMethod(method = "collectDirtyTagsToSave")
    private Map<SavedDataType<?>, CompoundTag> leafs$lockedCollectDirty(Operation<Map<SavedDataType<?>, CompoundTag>> original) {
        return SharedStateMonitor.call(this, original::call);
    }

    @WrapMethod(method = "encodeUnchecked")
    private <T extends SavedData> CompoundTag leafs$encodeOnDataMonitor(SavedDataType<T> type, SavedData data, RegistryOps<Tag> ops, Operation<CompoundTag> original) {
        return SharedStateMonitor.call(data, () -> original.call(type, data, ops));
    }
}
