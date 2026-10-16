package fr.hardel.leafs.fabric;

import fr.hardel.shared.transform.SharedTransform;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

final class KnotSharedTransformer implements InvocationHandler {
    private final IMixinTransformer mixin;

    private KnotSharedTransformer(IMixinTransformer mixin) {
        this.mixin = mixin;
    }

    static void install() {
        SharedTransform.load();
        Object delegate = read(FabricLauncherBase.getLauncher(), "classLoader");
        Field transformer = field(delegate.getClass(), "mixinTransformer");
        IMixinTransformer mixin = (IMixinTransformer) read(delegate, transformer);
        Object wrapped = Proxy.newProxyInstance(IMixinTransformer.class.getClassLoader(), new Class<?>[]{IMixinTransformer.class}, new KnotSharedTransformer(mixin));
        write(delegate, transformer, wrapped);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
        Object result = forward(method, arguments);
        if (method.getName().equals("transformClassBytes") && result instanceof byte[] bytes) {
            return rewrite((String) arguments[0], bytes);
        }

        return result;
    }

    private Object forward(Method method, Object[] arguments) throws Throwable {
        try {
            return method.invoke(mixin, arguments);
        } catch (InvocationTargetException thrown) {
            throw thrown.getCause();
        }
    }

    private static byte[] rewrite(String name, byte[] bytes) {
        String internalName = name.replace('.', '/');
        if (!SharedTransform.covers(internalName)) {
            return bytes;
        }

        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.EXPAND_FRAMES);
        if (!SharedTransform.apply(node, KnotSharedTransformer::superName)) {
            return bytes;
        }

        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static String superName(String internalName) {
        byte[] bytes = classBytes(internalName.replace('/', '.'));
        return bytes == null ? null : new ClassReader(bytes).getSuperName();
    }

    private static byte[] classBytes(String name) {
        try {
            return FabricLauncherBase.getLauncher().getClassByteArray(name, false);
        } catch (IOException unreadable) {
            return null;
        }
    }

    private static Object read(Object owner, String name) {
        return read(owner, field(owner.getClass(), name));
    }

    private static Object read(Object owner, Field field) {
        try {
            return field.get(owner);
        } catch (IllegalAccessException failure) {
            throw unsupported(failure);
        }
    }

    private static void write(Object owner, Field field, Object value) {
        try {
            field.set(owner, value);
        } catch (IllegalAccessException failure) {
            throw unsupported(failure);
        }
    }

    private static Field field(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            Field found = declared(current, name);
            if (found != null) {
                found.setAccessible(true);
                return found;
            }
        }

        throw unsupported(new NoSuchFieldException("%s in %s".formatted(name, type.getName())));
    }

    private static Field declared(Class<?> type, String name) {
        try {
            return type.getDeclaredField(name);
        } catch (NoSuchFieldException absent) {
            return null;
        }
    }

    private static IllegalStateException unsupported(Exception cause) {
        return new IllegalStateException("Leafs could not hook the class transformer of this Fabric Loader to share the collections of mods", cause);
    }
}
