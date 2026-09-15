package fr.hardel.leafs.mixin.compat.utilitarian;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.SynchronizedArrayList;
import fr.hardel.excess.SynchronizedHashMap;
import fr.hardel.excess.SynchronizedLongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.HashMap;

/** JDKDigital/utilitarian, fast leaf decay. onLogBreak fills the queue and the visited sets from the breaking player's region while onServerTickPost walks and clears them. */
@Pseudo
@Mixin(targets = "cy.jdkdigital.utilitarian.event.ModEventHandler")
public abstract class ModEventHandlerMixin {

    @WrapOperation(method = "<clinit>", at = @At(value = "NEW", target = "java/util/HashMap"))
    private static <K, V> HashMap<K, V> leafs$sharedVisited(Operation<HashMap<K, V>> original) {
        return new SynchronizedHashMap<>();
    }

    @WrapOperation(method = "<clinit>", at = @At(value = "NEW", target = "java/util/ArrayList"))
    private static <E> ArrayList<E> leafs$sharedDecayQueue(Operation<ArrayList<E>> original) {
        return new SynchronizedArrayList<>();
    }

    @WrapOperation(method = "lambda$onLogBreak$0", at = @At(value = "NEW", target = "it/unimi/dsi/fastutil/longs/LongOpenHashSet"))
    private static LongOpenHashSet leafs$sharedVisitedPositions(Operation<LongOpenHashSet> original) {
        return new SynchronizedLongOpenHashSet();
    }
}
