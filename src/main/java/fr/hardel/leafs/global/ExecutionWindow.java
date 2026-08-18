package fr.hardel.leafs.global;

import fr.hardel.leafs.metrics.DeferReason;
import net.minecraft.server.MinecraftServer;

/**
 * Every command and every datapack function funnels through {@code Commands.executeCommandInContext},
 * whatever content triggers it: an advancement reward, an enchantment run_function effect, a sign
 * click command, a mod. A command reaches arbitrary state, so an execution triggered off the server
 * thread moves whole into the barrier window. The server thread keeps its vanilla paths untouched,
 * chat, console, rcon, tick tags and the window's own replays included, and a nested execution stays
 * inside its parent's context exactly like vanilla.
 */
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
