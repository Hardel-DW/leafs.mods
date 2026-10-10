package fr.hardel.leafs.mixin.ticking;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.ticking.TickOrders;
import fr.hardel.leafs.ticking.TickState;
import fr.hardel.leafs.ticking.TickingManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ServerTickRateManager.class)
public abstract class ServerTickRateManagerMixin implements TickOrders {

    @Shadow
    @Final
    private MinecraftServer server;

    @Shadow
    private long remainingSprintTicks;

    @Shadow
    private long sprintTimeSpend;

    @Shadow
    private boolean previousIsFrozen;

    @Unique
    private TickState.Order leafs$step = TickState.Order.NONE;

    @Unique
    private TickState.Order leafs$sprint = TickState.Order.NONE;

    @Unique
    private long leafs$sprintStartNanos;

    @Shadow
    public abstract boolean isSprinting();

    @Shadow
    private void finishTickSprint() {
        throw new IllegalStateException("Shadowed method body");
    }

    @Override
    public TickState.Order leafs$step() {
        return new TickState.Order(leafs$step.id(), leafs$step.ticks(), leafs$self().frozenTicksToRun());
    }

    @Override
    public TickState.Order leafs$sprint() {
        return new TickState.Order(leafs$sprint.id(), leafs$sprint.ticks(), remainingSprintTicks);
    }

    public boolean isSteppingForward() {
        return leafs$self().frozenTicksToRun() > 0 || !TickingManager.of(server).everyRegion(time -> time.stepped(leafs$step));
    }

    @WrapMethod(method = "stepGameIfPaused")
    private boolean leafs$orderTheStep(int ticks, Operation<Boolean> original) {
        boolean stepping = original.call(ticks);
        if (stepping) {
            leafs$step = leafs$next(leafs$step, ticks);
        }

        return stepping;
    }

    @WrapMethod(method = "stopStepping")
    private boolean leafs$stopTheStepEverywhere(Operation<Boolean> original) {
        boolean stepping = isSteppingForward();
        original.call();
        if (stepping) {
            leafs$step = leafs$next(leafs$step, 0);
        }

        return stepping;
    }

    @WrapMethod(method = "requestGameToSprint")
    private boolean leafs$orderTheSprint(int time, Operation<Boolean> original) {
        boolean interrupted = isSprinting();
        original.call(time);
        leafs$sprint = leafs$next(leafs$sprint, time);
        leafs$sprintStartNanos = System.nanoTime();
        return interrupted;
    }

    @WrapMethod(method = "stopSprinting")
    private boolean leafs$stopTheSprintEverywhere(Operation<Boolean> original) {
        if (remainingSprintTicks > 0 || !isSprinting()) {
            return original.call();
        }

        finishTickSprint();
        return true;
    }

    @WrapMethod(method = "checkShouldSprintThisTick")
    private boolean leafs$sprintUntilTheLastRegion(Operation<Boolean> original) {
        if (remainingSprintTicks > 0) {
            return original.call();
        }

        if (TickingManager.of(server).everyRegion(time -> time.sprinted(leafs$sprint))) {
            finishTickSprint();
            return false;
        }

        leafs$self().isFrozen = previousIsFrozen;
        return false;
    }

    @WrapMethod(method = "finishTickSprint")
    private void leafs$reportTheWholeSprint(Operation<Void> original) {
        leafs$sprint = leafs$next(leafs$sprint, 0);
        sprintTimeSpend = System.nanoTime() - leafs$sprintStartNanos;
        original.call();
    }

    @Unique
    private static TickState.Order leafs$next(TickState.Order previous, long ticks) {
        return new TickState.Order(previous.id() + 1, ticks, 0L);
    }

    @Unique
    private ServerTickRateManager leafs$self() {
        return (ServerTickRateManager) (Object) this;
    }
}
