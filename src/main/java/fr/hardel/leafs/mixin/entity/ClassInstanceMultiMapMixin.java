package fr.hardel.leafs.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.global.SharedStateMonitor;
import net.minecraft.util.ClassInstanceMultiMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** An entity section read across a region seam: copy-on-write lists a reader walks lock-free, the class cache and the writes under the section's monitor. */
@Mixin(ClassInstanceMultiMap.class)
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

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object leafs$concurrentStores(Map<Class<?>, List<T>> instance, Object key, Object value, Operation<Object> original) {
        this.byClass = new ConcurrentHashMap<>();
        this.allInstances = new CopyOnWriteArrayList<>();
        return this.byClass.put(baseClass, allInstances);
    }

    /** The class cache fills from a plain list vanilla builds; it is stored as a copy-on-write one. */
    @WrapOperation(method = "find", at = @At(value = "INVOKE", target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"))
    private Object leafs$copyOnWriteClassList(Map<Class<?>, List<T>> instance, Object key, Function<Class<?>, List<T>> mapping, Operation<Object> original) {
        Function<Class<?>, List<T>> copyOnWrite = type -> new CopyOnWriteArrayList<>(mapping.apply(type));
        return original.call(instance, key, copyOnWrite);
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
