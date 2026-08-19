package fr.hardel.leafs.chunk.core;

import fr.hardel.leafs.Leafs;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.invoke.MethodHandle;
import java.util.Locale;

import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Linux ignores {@link Thread#setPriority}, so a worker lowers its own OS priority through the
 * libc {@code setpriority} call instead. Nice is per thread on Linux and lowering it needs no
 * privilege. Windows and macOS honour the Java priority natively and resolve nothing here.
 */
final class NativeThreadPriority {
    private static final int PRIO_PROCESS = 0;
    private static final int CALLING_THREAD = 0;
    private static final int LOWEST_NICE = 19;
    private static final MethodHandle SET_PRIORITY = resolve();

    private NativeThreadPriority() {
    }

    static void lowerCurrentThread() {
        if (SET_PRIORITY == null)
            return;

        try {
            int result = (int) SET_PRIORITY.invokeExact(PRIO_PROCESS, CALLING_THREAD, LOWEST_NICE);
            if (result != 0) {
                Leafs.LOGGER.warn("setpriority refused the nice change on {}, the worker keeps normal OS priority", Thread.currentThread().getName());
            }
        } catch (Throwable throwable) {
            Leafs.LOGGER.warn("Native setpriority call failed, the worker keeps normal OS priority", throwable);
        }
    }

    private static MethodHandle resolve() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux")) {
            return null;
        }

        try {
            Linker linker = Linker.nativeLinker();
            return linker.defaultLookup().find("setpriority")
                .map(symbol -> linker.downcallHandle(symbol, FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT)))
                .orElse(null);
        } catch (Throwable throwable) {
            Leafs.LOGGER.warn("Unable to bind setpriority, chunk workers keep normal OS priority", throwable);
            return null;
        }
    }
}
