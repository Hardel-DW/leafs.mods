package fr.hardel.shared.transform;

import fr.hardel.shared.SharedCollection;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedTransformTest {
    private static final String FIXTURES = "fixture.shared.";
    private static final Supers SUPERS = name -> name.equals("fixture/shared/BaseBlock") ? "net/minecraft/world/level/block/Block" : null;

    private final TransformingLoader loader = new TransformingLoader();

    @Test
    void aStaticCollectionIsSharedFromItsBirth() throws ReflectiveOperationException {
        Class<?> state = loader.loadClass("%sModState".formatted(FIXTURES));

        assertInstanceOf(SharedCollection.class, field(state, "COUNTS"));
        assertInstanceOf(SharedCollection.class, field(state, "buffer"));
        assertFalse(field(state, "FACTORY") instanceof SharedCollection);
    }

    /** The collection is shared at its birth, so the local the static initializer fills is the facade itself and nothing is lost. */
    @Test
    void aCollectionFilledThroughALocalKeepsWhatItReceived() throws ReflectiveOperationException {
        Class<?> state = loader.loadClass("%sModState".formatted(FIXTURES));

        assertInstanceOf(SharedCollection.class, field(state, "FILLED"));
        assertEquals(List.of("a"), field(state, "FILLED"));
    }

    /** 2026-10-16: exmachina rebuilds its buffer in drain(), and the hand written patch only covered the constructor. */
    @Test
    void aCollectionBuiltAgainInAMethodIsSharedToo() throws ReflectiveOperationException {
        Class<?> state = loader.loadClass("%sModState".formatted(FIXTURES));
        state.getMethod("drain").invoke(null);

        assertInstanceOf(SharedCollection.class, field(state, "buffer"));
    }

    @Test
    void aMapOfCollectionsSharesWhatComputeCreates() throws ReflectiveOperationException {
        Map<?, ?> nested = (Map<?, ?>) field(loader.loadClass("%sModState".formatted(FIXTURES)), "NESTED");
        Object created = Map.class.getMethod("computeIfAbsent", Object.class, Function.class).invoke(nested, "a", (Function<Object, Object>) _ -> new ArrayList<>());

        assertInstanceOf(SharedCollection.class, created);
    }

    @Test
    void theFieldsOfAnObjectTheWorldSharesAreShared() throws ReflectiveOperationException {
        Object manager = field(loader.loadClass("%sModManager".formatted(FIXTURES)), "INSTANCE");
        Object registry = loader.loadClass("%sModRegistry".formatted(FIXTURES)).getEnumConstants()[0];
        Object block = loader.loadClass("%sModBlock".formatted(FIXTURES)).getConstructor().newInstance();

        assertInstanceOf(SharedCollection.class, instanceField(manager, "entries"));
        assertInstanceOf(SharedCollection.class, instanceField(registry, "values"));
        assertInstanceOf(SharedCollection.class, instanceField(block, "shapes"));
    }

    @Test
    void theFieldsOfAnEntityStayItsOwn() throws ReflectiveOperationException {
        Object entity = loader.loadClass("%sModEntity".formatted(FIXTURES)).getConstructor().newInstance();

        assertSame(ArrayList.class, instanceField(entity, "owned").getClass());
    }

    @Test
    void thePlatformAndTheCollectionLibrariesAreNeverRewritten() {
        Set.of("net/minecraft/world/level/Level", "it/unimi/dsi/fastutil/longs/LongSet", "fr/hardel/leafs/Leafs", "java/util/HashMap", "com/google/common/collect/Maps")
            .forEach(name -> assertFalse(SharedTransform.covers(name), name));
        assertTrue(SharedTransform.covers("com/example/mod/ModState"));
    }

    private static Object field(Class<?> owner, String name) throws ReflectiveOperationException {
        return owner.getField(name).get(null);
    }

    private static Object instanceField(Object owner, String name) throws ReflectiveOperationException {
        return owner.getClass().getField(name).get(owner);
    }

    /** Loads the fixtures from their rewritten bytecode, everything else from the test class path. */
    private static final class TransformingLoader extends ClassLoader {

        private TransformingLoader() {
            super(SharedTransformTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith(FIXTURES)) {
                return super.loadClass(name, resolve);
            }

            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded != null) {
                    return loaded;
                }

                byte[] bytes = rewritten(name);
                return defineClass(name, bytes, 0, bytes.length);
            }
        }

        private byte[] rewritten(String name) throws ClassNotFoundException {
            ClassNode node = new ClassNode();
            new ClassReader(original(name)).accept(node, ClassReader.EXPAND_FRAMES);
            SharedTransform.apply(node, SUPERS);
            ClassWriter writer = new ClassWriter(0);
            node.accept(writer);
            return writer.toByteArray();
        }

        private byte[] original(String name) throws ClassNotFoundException {
            try (InputStream stream = getParent().getResourceAsStream("%s.class".formatted(name.replace('.', '/')))) {
                if (stream == null) {
                    throw new ClassNotFoundException(name);
                }

                return stream.readAllBytes();
            } catch (IOException failure) {
                throw new ClassNotFoundException(name, failure);
            }
        }
    }
}
