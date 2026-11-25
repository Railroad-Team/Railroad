package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.classparser.Type;
import dev.railroadide.railroad.ide.classparser.stub.*;
import org.objectweb.asm.Opcodes;

import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Renders compiled class metadata as declaration-only Java source, for navigating into binaries without attached
 * sources. Types are written fully qualified so that the rendered declarations resolve to the same identities as
 * the binary they describe; members have no bodies or initializers.
 */
public final class JavaStubSourceRenderer {
    private static final String INDENT = "    ";

    private final Map<String, List<ClassStub>> nestedByEnclosing;
    private final StringBuilder output = new StringBuilder();

    private JavaStubSourceRenderer(Map<String, List<ClassStub>> nestedByEnclosing) {
        this.nestedByEnclosing = nestedByEnclosing;
    }

    /**
     * Renders a top-level class and its named member classes.
     *
     * @param topLevel top-level class metadata
     * @param classStubs every class known to the index, used to find nested member classes
     * @param origin human-readable address of the compiled class, shown in the header comment
     * @return declaration-only Java source ending with a newline
     */
    public static String render(ClassStub topLevel, Map<String, ClassStub> classStubs, String origin) {
        String prefix = topLevel.getFullName() + "$";
        Map<String, List<ClassStub>> nested = new HashMap<>();
        for (Map.Entry<String, ClassStub> entry : classStubs.entrySet()) {
            ClassStub stub = entry.getValue();
            if (entry.getKey().startsWith(prefix) && stub.enclosingClassName() != null
                && isMemberClass(stub)) {
                nested.computeIfAbsent(stub.enclosingClassName(), _ -> new ArrayList<>()).add(stub);
            }
        }

        nested.values().forEach(list -> list.sort(Comparator.comparing(ClassStub::name)));

        var renderer = new JavaStubSourceRenderer(nested);
        renderer.output.append("// Compiled code: no attached source was found for\n// ").append(origin)
            .append("\n// Only declarations are shown.\n\n");
        if (topLevel.packageName() != null && !topLevel.packageName().isEmpty()) {
            renderer.output.append("package ").append(topLevel.packageName()).append(";\n\n");
        }

        renderer.renderClass(topLevel, 0);
        return renderer.output.toString();
    }

    private static boolean isMemberClass(ClassStub stub) {
        String simple = simpleName(stub);
        return !simple.isEmpty() && Character.isJavaIdentifierStart(simple.charAt(0))
            && (stub.modifiers() & Opcodes.ACC_SYNTHETIC) == 0;
    }

    private static String simpleName(ClassStub stub) {
        return stub.name().substring(stub.name().lastIndexOf('$') + 1);
    }

    private void renderClass(ClassStub stub, int depth) {
        int flags = stub.modifiers();
        boolean annotation = (flags & Opcodes.ACC_ANNOTATION) != 0;
        boolean isInterface = Modifier.isInterface(flags);
        boolean isEnum = (flags & Opcodes.ACC_ENUM) != 0;
        boolean isRecord = (flags & Opcodes.ACC_RECORD) != 0;

        int modifierMask = Modifier.classModifiers();
        if (isInterface || isEnum) {
            modifierMask &= ~(Modifier.ABSTRACT | Modifier.FINAL);
        }

        if (isRecord) {
            modifierMask &= ~Modifier.FINAL;
        }

        if (depth == 0) {
            modifierMask &= ~(Modifier.STATIC | Modifier.PRIVATE | Modifier.PROTECTED);
        }

        indent(depth);
        appendModifiers(flags & modifierMask);
        output.append(annotation
            ? "@interface "
            : isInterface
                ? "interface "
                : isEnum
                    ? "enum "
                    : isRecord ? "record " : "class ")
            .append(simpleName(stub));
        appendTypeParameters(stub.typeParameters());

        List<FieldStub> components = isRecord
            ? stub.fields().stream().filter(field -> !Modifier.isStatic(field.modifiers())).toList()
            : List.of();
        if (isRecord) {
            output.append('(');
            for (int index = 0; index < components.size(); index++) {
                if (index > 0) {
                    output.append(", ");
                }

                output.append(type(components.get(index).type())).append(' ').append(components.get(index).name());
            }

            output.append(')');
        }

        List<Type> interfaces = annotation
            ? List.of()
            : stub.interfaces();
        if (!isInterface && !isEnum && !isRecord && stub.superClass() != null
            && !(stub.superClass() instanceof Type.ClassType named && named.name().equals("java.lang.Object"))) {
            output.append(" extends ").append(type(stub.superClass()));
        }

        if (!interfaces.isEmpty()) {
            output.append(isInterface ? " extends " : " implements ");
            appendTypes(interfaces);
        }

        output.append(" {\n");

        int memberDepth = depth + 1;
        if (isEnum) {
            List<String> constants = stub.fields().stream()
                .filter(field -> (field.modifiers() & Opcodes.ACC_ENUM) != 0)
                .map(FieldStub::name)
                .toList();
            indent(memberDepth);
            output.append(String.join(", ", constants)).append(";\n");
        }

        for (FieldStub field : stub.fields()) {
            if ((field.modifiers() & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_ENUM)) != 0 || components.contains(field))
                continue;

            indent(memberDepth);
            appendModifiers(field.modifiers() & Modifier.fieldModifiers());
            output.append(type(field.type())).append(' ').append(field.name()).append(";\n");
        }

        for (ConstructorStub constructor : stub.constructors()) {
            if ((constructor.modifiers() & Opcodes.ACC_SYNTHETIC) != 0)
                continue;

            indent(memberDepth);
            appendModifiers(constructor.modifiers() & Modifier.constructorModifiers());
            appendTypeParameters(constructor.typeParameters());
            if (!constructor.typeParameters().isEmpty()) {
                output.append(' ');
            }

            output.append(simpleName(stub));
            appendParameters(constructor.parameters(), constructor.modifiers());
            appendThrows(constructor.thrownTypes());
            output.append(" {}\n");
        }

        for (MethodStub method : stub.methods()) {
            int methodFlags = method.modifiers();
            if ((methodFlags & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0 || method.name().startsWith("<"))
                continue;

            boolean bodiless = Modifier.isAbstract(methodFlags) || Modifier.isNative(methodFlags);
            int mask = Modifier.methodModifiers();
            if (isInterface) {
                mask &= ~(Modifier.ABSTRACT | Modifier.PUBLIC);
            }

            indent(memberDepth);
            appendModifiers(methodFlags & mask);
            if (isInterface && !bodiless && !Modifier.isStatic(methodFlags) && !Modifier.isPrivate(methodFlags)) {
                output.append("default ");
            }

            if (!method.typeParameters().isEmpty()) {
                appendTypeParameters(method.typeParameters());
                output.append(' ');
            }

            output.append(type(method.returnType())).append(' ').append(method.name());
            appendParameters(method.parameters(), methodFlags);
            appendThrows(method.thrownTypes());
            output.append(bodiless ? ";\n" : " {}\n");
        }

        for (ClassStub nested : nestedByEnclosing.getOrDefault(stub.getFullName(), List.of())) {
            output.append('\n');
            renderClass(nested, memberDepth);
        }

        indent(depth);
        output.append("}\n");
    }

    private void appendModifiers(int flags) {
        String modifiers = Modifier.toString(flags);
        if (!modifiers.isEmpty()) {
            output.append(modifiers).append(' ');
        }
    }

    private void appendTypeParameters(List<TypeParameter> parameters) {
        if (parameters.isEmpty())
            return;

        output.append('<');
        for (int index = 0; index < parameters.size(); index++) {
            if (index > 0) {
                output.append(", ");
            }

            TypeParameter parameter = parameters.get(index);
            output.append(parameter.name());
            List<Type> bounds = parameter.bounds().stream()
                .filter(bound -> bound != null
                    && !(bound instanceof Type.ClassType named && named.name().equals("java.lang.Object")))
                .toList();
            if (!bounds.isEmpty()) {
                output.append(" extends ");
                for (int bound = 0; bound < bounds.size(); bound++) {
                    if (bound > 0) {
                        output.append(" & ");
                    }

                    output.append(type(bounds.get(bound)));
                }
            }
        }

        output.append('>');
    }

    private void appendParameters(List<Parameter> parameters, int flags) {
        output.append('(');
        boolean varargs = (flags & Opcodes.ACC_VARARGS) != 0;
        for (int index = 0; index < parameters.size(); index++) {
            if (index > 0) {
                output.append(", ");
            }

            Parameter parameter = parameters.get(index);
            if (varargs && index == parameters.size() - 1 && parameter.type() instanceof Type.ArrayType array) {
                output.append(type(array.componentType())).append("...");
            } else {
                output.append(type(parameter.type()));
            }

            output.append(' ').append(parameter.name() == null || parameter.name().isBlank()
                ? "arg" + index
                : parameter.name());
        }

        output.append(')');
    }

    private void appendThrows(List<Type> thrown) {
        if (thrown.isEmpty())
            return;

        output.append(" throws ");
        appendTypes(thrown);
    }

    private void appendTypes(List<Type> types) {
        for (int index = 0; index < types.size(); index++) {
            if (index > 0) {
                output.append(", ");
            }

            output.append(type(types.get(index)));
        }
    }

    private void indent(int depth) {
        output.append(INDENT.repeat(depth));
    }

    private static String type(Type value) {
        return switch (value) {
            case Type.ClassType named -> named.typeArguments().isEmpty()
                ? named.name().replace('$', '.')
                : named.name().replace('$', '.') + "<"
                    + String.join(", ", named.typeArguments().stream().map(JavaStubSourceRenderer::type).toList())
                    + ">";
            case Type.PrimitiveType primitive -> primitive.name();
            case Type.ArrayType array -> type(array.componentType()) + "[]";
            case Type.TypeVariable variable -> variable.name();
            case Type.WildcardType wildcard -> wildcard.bound() == null
                || wildcard.isUpperBound() && wildcard.bound() instanceof Type.ClassType named
                    && named.name().equals("java.lang.Object")
                        ? "?"
                        : (wildcard.isUpperBound() ? "? extends " : "? super ") + type(wildcard.bound());
        };
    }
}
