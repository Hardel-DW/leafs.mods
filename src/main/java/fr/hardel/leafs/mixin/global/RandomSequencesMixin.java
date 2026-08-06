package fr.hardel.leafs.mixin.global;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.global.LockedRandomSource;
import fr.hardel.leafs.global.SharedStateMonitor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomSequence;
import net.minecraft.world.RandomSequences;
import org.spongepowered.asm.mixin.Mixin;

import java.util.function.BiConsumer;

/** Per-instance lock: loot rolls from any region share sequence sources; one monitor orders rolls against save encode. */
@Mixin(RandomSequences.class)
public abstract class RandomSequencesMixin {

    @WrapMethod(method = "get")
    private RandomSource leafs$lockedGet(Identifier key, long worldSeed, Operation<RandomSource> original) {
        return new LockedRandomSource(SharedStateMonitor.call(this, () -> original.call(key, worldSeed)), this);
    }

    @WrapMethod(method = "forAllSequences")
    private void leafs$lockedForAllSequences(BiConsumer<Identifier, RandomSequence> action, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(action));
    }

    @WrapMethod(method = "setSeedDefaults")
    private void leafs$lockedSetSeedDefaults(int salt, boolean includeWorldSeed, boolean includeSequenceId, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(salt, includeWorldSeed, includeSequenceId));
    }

    @WrapMethod(method = "clear")
    private int leafs$lockedClear(Operation<Integer> original) {
        return SharedStateMonitor.call(this, original::call);
    }

    @WrapMethod(method = "reset(Lnet/minecraft/resources/Identifier;J)V")
    private void leafs$lockedReset(Identifier id, long worldSeed, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(id, worldSeed));
    }

    @WrapMethod(method = "reset(Lnet/minecraft/resources/Identifier;JIZZ)V")
    private void leafs$lockedResetSeeded(Identifier id, long worldSeed, int salt, boolean includeWorldSeed, boolean includeSequenceId, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(id, worldSeed, salt, includeWorldSeed, includeSequenceId));
    }
}
