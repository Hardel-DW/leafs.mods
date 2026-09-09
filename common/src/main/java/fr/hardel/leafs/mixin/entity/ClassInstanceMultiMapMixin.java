package fr.hardel.leafs.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.excess.CopyOnWriteListMap;
import fr.hardel.leafs.global.SharedStateMonitor;
import net.minecraft.util.ClassInstanceMultiMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** An entity section read across a region seam: copy-on-write lists a reader walks lock-free, writes under the section's monitor. The map makes every list copy-on-write whoever builds {@code find}; the wraps land after Lithium's overwrite. */
@Mixin(value = ClassInstanceMultiMap.class, priority = 1100)
public abstract class ClassInstanceMultiMapMixin<T> {

    @Mutable
    @Shadow
    @Final
    private Map<Class<?>, List<T>> byClass;

    @Mutable
    @Shadow
    @Final
    private List<T> allInstances;

    @Shadow
    @Final
    private Class<T> baseClass;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$concurrentStores(CallbackInfo callbackInfo) {
        this.byClass = new CopyOnWriteListMap<>();
        this.allInstances = new CopyOnWriteArrayList<>();
        this.byClass.put(baseClass, allInstances);
    }

    @WrapMethod(method = "add")
    private boolean leafs$lockedAdd(T instance, Operation<Boolean> original) {
        return SharedStateMonitor.call(this, () -> original.call(instance));
    }

    @WrapMethod(method = "remove")
    private boolean leafs$lockedRemove(Object object, Operation<Boolean> original) {
        return SharedStateMonitor.call(this, () -> original.call(object));
    }

    @WrapMethod(method = "find")
    private <S> Collection<S> leafs$lockedFind(Class<S> index, Operation<Collection<S>> original) {
        return SharedStateMonitor.call(this, () -> original.call(index));
    }
}
