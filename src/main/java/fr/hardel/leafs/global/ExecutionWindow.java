package fr.hardel.leafs.global;

import fr.hardel.leafs.metrics.DeferReason;
import net.minecraft.server.MinecraftServer;

/** Every command and function passes {@code Commands.executeCommandInContext}; one triggered off the server thread moves whole into the barrier window. */
public final class ExecutionWindow {

    private ExecutionWindow() {
    }

    public static boolean defer(MinecraftServer server, Runnable execution) {
        if (server.isSameThread()) {
            return false;
        }

        BarrierWindow.of(server).enqueue(DeferReason.COMMAND_EXECUTION, execution);
        return true;
    }
}
