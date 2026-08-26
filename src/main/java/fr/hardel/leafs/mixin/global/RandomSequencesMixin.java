package fr.hardel.leafs.mixin.global;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.global.LockedRandomSource;
import fr.hardel.leafs.global.SharedStateMonitor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomSequence;
import net.minecraft.world.RandomSequences;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;
import java.util.function.BiConsumer;

/** A roll locks its own sequence, the map stays under the instance monitor. Lock order: instance then sequence. */
@Mixin(RandomSequences.class)
public abstract class RandomSequencesMixin {

    @Shadow
    @Final
    private Map<Identifier, RandomSequence> sequences;

    @WrapMethod(method = "get")
    private RandomSource leafs$lockedGet(Identifier key, long worldSeed, Operation<RandomSource> original) {
        RandomSource source = SharedStateMonitor.call(this, () -> original.call(key, worldSeed));
        return new LockedRandomSource(source, sequences.get(key));
    }

    /** The visit holds each sequence in turn, which is what orders a save encode against a concurrent roll. */
    @WrapMethod(method = "forAllSequences")
    private void leafs$lockedForAllSequences(BiConsumer<Identifier, RandomSequence> action, Operation<Void> original) {
        BiConsumer<Identifier, RandomSequence> locked = (id, sequence) -> {
            synchronized (sequence) {
                action.accept(id, sequence);
            }
        };
        SharedStateMonitor.run(this, () -> original.call(locked));
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
