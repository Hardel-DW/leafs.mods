package fr.hardel.leafs.mixin.global;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.global.DeferredFileWrites;
import net.minecraft.stats.ServerStatsCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;

/** The stats JSON serializes on the caller into the IO writer; the login read sees pending writes first. */
@Mixin(ServerStatsCounter.class)
public abstract class ServerStatsCounterMixin {

    @WrapOperation(method = "save", at = @At(value = "INVOKE", target = "Ljava/nio/file/Files;newBufferedWriter(Ljava/nio/file/Path;Ljava/nio/charset/Charset;[Ljava/nio/file/OpenOption;)Ljava/io/BufferedWriter;"))
    private BufferedWriter leafs$deferredWriter(Path file, Charset charset, OpenOption[] options, Operation<BufferedWriter> original) throws IOException {
        DeferredFileWrites writes = DeferredFileWrites.active();
        if (writes == null) {
            return original.call(file, charset, options);
        }

        return new BufferedWriter(writes.textWriter(file));
    }

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/nio/file/Files;isRegularFile(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z"))
    private boolean leafs$pendingCountsAsFile(Path file, LinkOption[] options, Operation<Boolean> original) {
        DeferredFileWrites writes = DeferredFileWrites.active();
        return (writes != null && writes.pendingText(file) != null) || original.call(file, options);
    }

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/nio/file/Files;newBufferedReader(Ljava/nio/file/Path;Ljava/nio/charset/Charset;)Ljava/io/BufferedReader;"))
    private BufferedReader leafs$pendingAwareReader(Path file, Charset charset, Operation<BufferedReader> original) throws IOException {
        DeferredFileWrites writes = DeferredFileWrites.active();
        String pending = writes == null ? null : writes.pendingText(file);
        if (pending != null) {
            return new BufferedReader(new StringReader(pending));
        }

        return original.call(file, charset);
    }
}
