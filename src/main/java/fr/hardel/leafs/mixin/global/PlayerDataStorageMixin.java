package fr.hardel.leafs.mixin.global;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import fr.hardel.leafs.global.DeferredFileWrites;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Optional;

/** The playerdata NBT serializes on the caller; the temp write and the atomic replace defer to the IO writer. */
@Mixin(PlayerDataStorage.class)
public abstract class PlayerDataStorageMixin {

    @Shadow
    @Final
    private File playerDir;

    /** The temp file only needs a unique name, not a disk touch: the deferred write creates it. */
    @WrapOperation(method = "save", at = @At(value = "INVOKE", target = "Ljava/nio/file/Files;createTempFile(Ljava/nio/file/Path;Ljava/lang/String;Ljava/lang/String;[Ljava/nio/file/attribute/FileAttribute;)Ljava/nio/file/Path;"))
    private Path leafs$uncreatedTempFile(Path dir, String prefix, String suffix, FileAttribute<?>[] attributes, Operation<Path> original) {
        if (DeferredFileWrites.active() == null) {
            return original.call(dir, prefix, suffix, attributes);
        }

        return dir.resolve(prefix + Long.toUnsignedString(System.nanoTime(), 36) + suffix);
    }

    @WrapOperation(method = "save", at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/NbtIo;writeCompressed(Lnet/minecraft/nbt/CompoundTag;Ljava/nio/file/Path;)V"))
    private void leafs$captureTag(CompoundTag tag, Path tmpFile, Operation<Void> original, @Share("tag") LocalRef<CompoundTag> captured) {
        if (DeferredFileWrites.active() == null) {
            original.call(tag, tmpFile);
            return;
        }

        captured.set(tag);
    }

    @WrapOperation(method = "save", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;safeReplaceFile(Ljava/nio/file/Path;Ljava/nio/file/Path;Ljava/nio/file/Path;)V"))
    private void leafs$deferredReplace(Path realFile, Path tmpFile, Path oldFile, Operation<Void> original, @Share("tag") LocalRef<CompoundTag> captured) {
        CompoundTag tag = captured.get();
        if (tag == null) {
            original.call(realFile, tmpFile, oldFile);
            return;
        }

        DeferredFileWrites.active().writeNbt(realFile, tmpFile, oldFile, tag);
    }

    /** A rejoin during the write window reads the pending tag, never a stale or missing file. */
    @WrapMethod(method = "load(Lnet/minecraft/server/players/NameAndId;Ljava/lang/String;)Ljava/util/Optional;")
    private Optional<CompoundTag> leafs$pendingAwareLoad(NameAndId nameAndId, String suffix, Operation<Optional<CompoundTag>> original) {
        DeferredFileWrites writes = DeferredFileWrites.active();
        if (writes != null) {
            CompoundTag tag = writes.pendingNbt(playerDir.toPath().resolve(nameAndId.id() + suffix));
            if (tag != null) {
                return Optional.of(tag.copy());
            }
        }

        return original.call(nameAndId, suffix);
    }
}
