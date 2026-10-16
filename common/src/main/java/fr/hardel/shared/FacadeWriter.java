package fr.hardel.shared;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Writes the facade of a collection class: a subclass whose public methods hold its monitor and call the original.
 * Views come back as {@link LockedView} proxies, forEach walks a copy outside the monitor, and compute methods share the collection they create.
 */
final class FacadeWriter implements Opcodes {
    private static final String NAME = "fr/hardel/shared/SharedFacade";
    private static final String OBJECT = "java/lang/Object";
    private static final String SNAPSHOT_DESCRIPTOR = "()Ljava/lang/Object;";
    private static final String VIEWS = Type.getInternalName(Views.class);
    private static final String VIEW_DESCRIPTOR = "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";
    private static final String SHARED = Type.getInternalName(Shared.class);
    private static final String WRAP_DESCRIPTOR = "(Ljava/lang/Object;)Ljava/lang/Object;";
    private static final Set<String> NESTING = Set.of("computeIfAbsent", "compute", "computeIfPresent", "merge");

    private final Class<?> type;
    private final String superName;
    private final String copyDescriptor;

    private FacadeWriter(Class<?> type, Constructor<?> copy) {
        this.type = type;
        this.superName = Type.getInternalName(type);
        this.copyDescriptor = Type.getConstructorDescriptor(copy);
    }

    static MethodHandle define(Class<?> type, Constructor<?> copy) {
        byte[] bytes = new FacadeWriter(type, copy).write();
        return constructorOf(type, bytes);
    }

    private static MethodHandle constructorOf(Class<?> type, byte[] bytes) {
        try {
            MethodHandles.Lookup facade = MethodHandles.lookup().defineHiddenClass(bytes, true);
            return facade.findConstructor(facade.lookupClass(), MethodType.methodType(void.class, type)).asType(MethodType.methodType(Object.class, Object.class));
        } catch (IllegalAccessException | NoSuchMethodException failure) {
            throw new IllegalStateException("Leafs could not define the shared facade of %s".formatted(type.getName()), failure);
        }
    }

    private byte[] write() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(String first, String second) {
                return OBJECT;
            }
        };
        writer.visit(V21, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, NAME, null, superName, new String[]{Type.getInternalName(SharedCollection.class)});
        writeConstructor(writer);
        writeSnapshot(writer);
        overridable().values().forEach(method -> writeOverride(writer, method));
        writer.visitEnd();
        return writer.toByteArray();
    }

    private Map<String, Method> overridable() {
        Map<String, Method> methods = new LinkedHashMap<>();
        Arrays.stream(type.getMethods())
            .filter(FacadeWriter::overridable)
            .forEach(method -> methods.putIfAbsent("%s%s".formatted(method.getName(), Type.getMethodDescriptor(method)), method));
        return methods;
    }

    private static boolean overridable(Method method) {
        int modifiers = method.getModifiers();
        Class<?> owner = method.getDeclaringClass();
        return !Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers) && !method.isBridge() && !method.isSynthetic() && !owner.isInterface() && owner != Object.class;
    }

    private void writeConstructor(ClassWriter writer) {
        MethodVisitor code = writer.visitMethod(ACC_PUBLIC, "<init>", "(L%s;)V".formatted(superName), null, null);
        code.visitCode();
        code.visitVarInsn(ALOAD, 0);
        code.visitVarInsn(ALOAD, 1);
        code.visitMethodInsn(INVOKESPECIAL, superName, "<init>", copyDescriptor, false);
        code.visitInsn(RETURN);
        code.visitMaxs(0, 0);
        code.visitEnd();
    }

    private void writeSnapshot(ClassWriter writer) {
        MethodVisitor code = writer.visitMethod(ACC_PUBLIC | ACC_SYNCHRONIZED, "leafs$snapshot", SNAPSHOT_DESCRIPTOR, null, null);
        code.visitCode();
        code.visitTypeInsn(NEW, superName);
        code.visitInsn(DUP);
        code.visitVarInsn(ALOAD, 0);
        code.visitMethodInsn(INVOKESPECIAL, superName, "<init>", copyDescriptor, false);
        code.visitInsn(ARETURN);
        code.visitMaxs(0, 0);
        code.visitEnd();
    }

    private void writeOverride(ClassWriter writer, Method method) {
        String descriptor = Type.getMethodDescriptor(method);
        if (method.getName().startsWith("forEach") && method.getReturnType() == void.class) {
            writeWalk(writer, method, descriptor);
            return;
        }

        if (Views.isView(method)) {
            writeView(writer, method, descriptor);
            return;
        }

        MethodVisitor code = writer.visitMethod(ACC_PUBLIC | ACC_SYNCHRONIZED, method.getName(), descriptor, null, null);
        code.visitCode();
        writeLockedBody(code, method, descriptor);
        code.visitMaxs(0, 0);
        code.visitEnd();
    }

    private void writeLockedBody(MethodVisitor code, Method method, String descriptor) {
        if (NESTING.contains(method.getName()) && !method.getReturnType().isPrimitive()) {
            writeNesting(code, method, descriptor, putFor(method));
            return;
        }

        invokeOriginal(code, method.getName(), descriptor);
        code.visitInsn(Type.getReturnType(descriptor).getOpcode(IRETURN));
    }

    private void writeWalk(ClassWriter writer, Method method, String descriptor) {
        MethodVisitor code = writer.visitMethod(ACC_PUBLIC, method.getName(), descriptor, null, null);
        code.visitCode();
        code.visitVarInsn(ALOAD, 0);
        code.visitMethodInsn(INVOKEVIRTUAL, NAME, "leafs$snapshot", SNAPSHOT_DESCRIPTOR, false);
        code.visitTypeInsn(CHECKCAST, superName);
        loadArguments(code, descriptor);
        code.visitMethodInsn(INVOKEVIRTUAL, superName, method.getName(), descriptor, false);
        code.visitInsn(RETURN);
        code.visitMaxs(0, 0);
        code.visitEnd();
    }

    private void writeView(ClassWriter writer, Method method, String descriptor) {
        String original = "leafs$%s".formatted(method.getName());
        MethodVisitor locked = writer.visitMethod(ACC_PRIVATE | ACC_SYNCHRONIZED, original, descriptor, null, null);
        locked.visitCode();
        invokeOriginal(locked, method.getName(), descriptor);
        locked.visitInsn(ARETURN);
        locked.visitMaxs(0, 0);
        locked.visitEnd();

        int held = argumentSlots(descriptor) + 1;
        int view = held + 1;
        String result = Type.getInternalName(method.getReturnType());
        Label outside = new Label();
        MethodVisitor code = writer.visitMethod(ACC_PUBLIC, method.getName(), descriptor, null, null);
        code.visitCode();
        code.visitVarInsn(ALOAD, 0);
        code.visitMethodInsn(INVOKESTATIC, "java/lang/Thread", "holdsLock", "(Ljava/lang/Object;)Z", false);
        code.visitVarInsn(ISTORE, held);
        code.visitVarInsn(ALOAD, 0);
        loadArguments(code, descriptor);
        code.visitMethodInsn(INVOKESPECIAL, NAME, original, descriptor, false);
        code.visitVarInsn(ASTORE, view);
        code.visitVarInsn(ILOAD, held);
        code.visitJumpInsn(IFEQ, outside);
        code.visitVarInsn(ALOAD, view);
        code.visitInsn(ARETURN);
        code.visitLabel(outside);
        code.visitVarInsn(ALOAD, 0);
        code.visitLdcInsn(method.getName());
        code.visitLdcInsn(descriptor);
        pushArgumentArray(code, Type.getArgumentTypes(descriptor));
        code.visitVarInsn(ALOAD, view);
        code.visitMethodInsn(INVOKESTATIC, VIEWS, "of", VIEW_DESCRIPTOR, false);
        code.visitTypeInsn(CHECKCAST, result);
        code.visitInsn(ARETURN);
        code.visitMaxs(0, 0);
        code.visitEnd();
    }

    /** The value the compute method returns is the one stored, so sharing it and storing it back covers maps of collections. */
    private void writeNesting(MethodVisitor code, Method method, String descriptor, Optional<Method> put) {
        invokeOriginal(code, method.getName(), descriptor);
        if (put.isEmpty()) {
            code.visitInsn(ARETURN);
            return;
        }

        int computed = argumentSlots(descriptor) + 1;
        int shared = computed + 1;
        Label same = new Label();
        code.visitVarInsn(ASTORE, computed);
        code.visitVarInsn(ALOAD, computed);
        code.visitMethodInsn(INVOKESTATIC, SHARED, "wrap", WRAP_DESCRIPTOR, false);
        code.visitVarInsn(ASTORE, shared);
        code.visitVarInsn(ALOAD, shared);
        code.visitVarInsn(ALOAD, computed);
        code.visitJumpInsn(IF_ACMPEQ, same);
        writeStore(code, descriptor, put.get(), shared);
        code.visitLabel(same);
        code.visitVarInsn(ALOAD, shared);
        code.visitTypeInsn(CHECKCAST, Type.getInternalName(method.getReturnType()));
        code.visitInsn(ARETURN);
    }

    private void writeStore(MethodVisitor code, String descriptor, Method put, int shared) {
        Type key = Type.getArgumentTypes(descriptor)[0];
        String putDescriptor = Type.getMethodDescriptor(put);
        code.visitVarInsn(ALOAD, 0);
        code.visitVarInsn(key.getOpcode(ILOAD), 1);
        code.visitVarInsn(ALOAD, shared);
        code.visitTypeInsn(CHECKCAST, Type.getInternalName(put.getParameterTypes()[1]));
        code.visitMethodInsn(INVOKEVIRTUAL, NAME, "put", putDescriptor, false);
        code.visitInsn(Type.getReturnType(putDescriptor).getSize() == 2 ? POP2 : POP);
    }

    private Optional<Method> putFor(Method compute) {
        Class<?> key = compute.getParameterTypes()[0];
        return Arrays.stream(type.getMethods())
            .filter(method -> method.getName().equals("put") && method.getParameterCount() == 2 && method.getParameterTypes()[0] == key)
            .filter(method -> !method.getParameterTypes()[1].isPrimitive() && method.getReturnType() != void.class && !Modifier.isStatic(method.getModifiers()))
            .findFirst();
    }

    private void invokeOriginal(MethodVisitor code, String name, String descriptor) {
        code.visitVarInsn(ALOAD, 0);
        loadArguments(code, descriptor);
        code.visitMethodInsn(INVOKESPECIAL, superName, name, descriptor, false);
    }

    private static void loadArguments(MethodVisitor code, String descriptor) {
        int slot = 1;
        for (Type argument : Type.getArgumentTypes(descriptor)) {
            code.visitVarInsn(argument.getOpcode(ILOAD), slot);
            slot += argument.getSize();
        }
    }

    private static int argumentSlots(String descriptor) {
        return Arrays.stream(Type.getArgumentTypes(descriptor)).mapToInt(Type::getSize).sum();
    }

    private static void pushArgumentArray(MethodVisitor code, Type[] arguments) {
        code.visitLdcInsn(arguments.length);
        code.visitTypeInsn(ANEWARRAY, OBJECT);
        int slot = 1;
        for (int index = 0; index < arguments.length; index++) {
            code.visitInsn(DUP);
            code.visitLdcInsn(index);
            code.visitVarInsn(arguments[index].getOpcode(ILOAD), slot);
            box(code, arguments[index]);
            code.visitInsn(AASTORE);
            slot += arguments[index].getSize();
        }
    }

    private static void box(MethodVisitor code, Type type) {
        String boxed = switch (type.getSort()) {
            case Type.BOOLEAN -> "java/lang/Boolean";
            case Type.CHAR -> "java/lang/Character";
            case Type.BYTE -> "java/lang/Byte";
            case Type.SHORT -> "java/lang/Short";
            case Type.INT -> "java/lang/Integer";
            case Type.FLOAT -> "java/lang/Float";
            case Type.LONG -> "java/lang/Long";
            case Type.DOUBLE -> "java/lang/Double";
            default -> null;
        };
        
        if (boxed != null) {
            code.visitMethodInsn(INVOKESTATIC, boxed, "valueOf", "(%s)L%s;".formatted(type.getDescriptor(), boxed), false);
        }
    }
}
