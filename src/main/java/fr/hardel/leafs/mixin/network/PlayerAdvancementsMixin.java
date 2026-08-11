package fr.hardel.leafs.mixin.network;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.network.JoinPreload;
import net.minecraft.server.PlayerAdvancements;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Optional;

/** The join preload serves the advancements read at construction, so the flip into the game touches no disk. */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {

    @WrapOperation(method = "load(Lnet/minecraft/server/ServerAdvancementManager;)V", at = @At(value = "INVOKE", target = "Ljava/nio/file/Files;newBufferedReader(Ljava/nio/file/Path;Ljava/nio/charset/Charset;)Ljava/io/BufferedReader;"))
    private BufferedReader leafs$preloadAwareReader(Path file, Charset charset, Operation<BufferedReader> original) throws IOException {
        Optional<String> preloaded = JoinPreload.consumeContent(file);
        if (preloaded.isPresent()) {
            return new BufferedReader(new StringReader(preloaded.get()));
        }

        return original.call(file, charset);
    }
}
