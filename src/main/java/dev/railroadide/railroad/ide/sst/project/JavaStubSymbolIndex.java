package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.classparser.stub.ClassStub;
import dev.railroadide.railroad.ide.classparser.stub.ConstructorStub;
import dev.railroadide.railroad.ide.classparser.stub.FieldStub;
import dev.railroadide.railroad.ide.classparser.stub.MethodStub;
import dev.railroadide.railroad.ide.classparser.stub.Parameter;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolKind;
import org.objectweb.asm.Opcodes;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class JavaStubSymbolIndex implements JavaSymbolIndex {
    private final Set<String> declaredQualifiedNames;
    private final Set<String> packages;
    private final Map<String, ClassStub> classStubsByQualifiedName;
    private final Map<String, List<JavaProjectSemanticIndex.SymbolDescriptor>> symbolsBySimpleName;
    private final Map<String, List<JavaProjectSemanticIndex.SymbolDescriptor>> symbolsByQualifiedName;
    private final Map<String, List<JavaProjectSemanticIndex.SymbolDescriptor>> membersByOwnerQualifiedName;

    public JavaStubSymbolIndex(Map<String, ClassStub> classStubsByQualifiedName, Map<String, Path> sourceByQualifiedName) {
        this.classStubsByQualifiedName = Map.copyOf(Objects.requireNonNull(classStubsByQualifiedName, "classStubsByQualifiedName"));

        Set<String> declaredQualifiedNames = new LinkedHashSet<>();
        Set<String> packages = new LinkedHashSet<>();
        Map<String, List<JavaProjectSemanticIndex.SymbolDescriptor>> symbolsBySimpleName = new LinkedHashMap<>();
        Map<String, List<JavaProjectSemanticIndex.SymbolDescriptor>> symbolsByQualifiedName = new LinkedHashMap<>();
        Map<String, List<JavaProjectSemanticIndex.SymbolDescriptor>> membersByOwnerQualifiedName = new LinkedHashMap<>();

        for (Map.Entry<String, ClassStub> entry : this.classStubsByQualifiedName.entrySet()) {
            String qualifiedName = entry.getKey();
            ClassStub stub = entry.getValue();
            Path sourceFile = Objects.requireNonNull(sourceByQualifiedName.get(qualifiedName), "Missing source for " + qualifiedName);
            declaredQualifiedNames.add(qualifiedName);
            if (stub.packageName() != null && !stub.packageName().isBlank())
                packages.add(stub.packageName());

            JavaProjectSemanticIndex.SymbolDescriptor typeDescriptor = new JavaProjectSemanticIndex.SymbolDescriptor(
                toTypeKind(stub),
                stub.name(),
                qualifiedName,
                null,
                null,
                sourceFile,
                java.lang.reflect.Modifier.isStatic(stub.modifiers()),
                true
            );
            index(symbolsBySimpleName, typeDescriptor.simpleName(), typeDescriptor);
            index(symbolsByQualifiedName, qualifiedName, typeDescriptor);

            for (FieldStub field : stub.fields()) {
                JavaProjectSemanticIndex.SymbolDescriptor descriptor = new JavaProjectSemanticIndex.SymbolDescriptor(
                    SymbolKind.FIELD,
                    field.name(),
                    qualifiedName + "#" + field.name(),
                    qualifiedName,
                    null,
                    sourceFile,
                    java.lang.reflect.Modifier.isStatic(field.modifiers()),
                    false
                );
                index(symbolsBySimpleName, descriptor.simpleName(), descriptor);
                index(symbolsByQualifiedName, descriptor.qualifiedName(), descriptor);
                index(membersByOwnerQualifiedName, qualifiedName, descriptor);
            }

            for (MethodStub method : stub.methods()) {
                JavaProjectSemanticIndex.SymbolDescriptor descriptor = new JavaProjectSemanticIndex.SymbolDescriptor(
                    SymbolKind.METHOD,
                    method.name(),
                    qualifiedName + "#" + method.name(),
                    qualifiedName,
                    renderSignature(method),
                    sourceFile,
                    java.lang.reflect.Modifier.isStatic(method.modifiers()),
                    false
                );
                index(symbolsBySimpleName, descriptor.simpleName(), descriptor);
                index(symbolsByQualifiedName, descriptor.qualifiedName(), descriptor);
                index(membersByOwnerQualifiedName, qualifiedName, descriptor);
            }

            for (ConstructorStub constructor : stub.constructors()) {
                JavaProjectSemanticIndex.SymbolDescriptor descriptor = new JavaProjectSemanticIndex.SymbolDescriptor(
                    SymbolKind.CONSTRUCTOR,
                    stub.name(),
                    qualifiedName + "#<init>",
                    qualifiedName,
                    renderSignature(constructor),
                    sourceFile,
                    false,
                    false
                );
                index(symbolsBySimpleName, descriptor.simpleName(), descriptor);
                index(symbolsByQualifiedName, descriptor.qualifiedName(), descriptor);
                index(membersByOwnerQualifiedName, qualifiedName, descriptor);
            }
        }

        this.declaredQualifiedNames = Set.copyOf(declaredQualifiedNames);
        this.packages = Set.copyOf(packages);
        this.symbolsBySimpleName = copyListMap(symbolsBySimpleName);
        this.symbolsByQualifiedName = copyListMap(symbolsByQualifiedName);
        this.membersByOwnerQualifiedName = copyListMap(membersByOwnerQualifiedName);
    }

    @Override
    public Set<String> declaredQualifiedNames() {
        return declaredQualifiedNames;
    }

    @Override
    public boolean containsPackage(String packageName) {
        return packageName != null && packages.contains(packageName.trim());
    }

    @Override
    public Map<String, ClassStub> classStubsByQualifiedName() {
        return classStubsByQualifiedName;
    }

    @Override
    public List<JavaProjectSemanticIndex.SymbolDescriptor> lookupSimpleName(String simpleName) {
        return lookup(symbolsBySimpleName, simpleName);
    }

    @Override
    public List<JavaProjectSemanticIndex.SymbolDescriptor> lookupQualifiedName(String qualifiedName) {
        return lookup(symbolsByQualifiedName, qualifiedName);
    }

    @Override
    public List<JavaProjectSemanticIndex.SymbolDescriptor> lookupMembers(String qualifiedName) {
        return lookup(membersByOwnerQualifiedName, qualifiedName);
    }

    @Override
    public List<JavaProjectSemanticIndex.SymbolDescriptor> lookupMember(String ownerQualifiedName, String simpleName) {
        return lookupMembers(ownerQualifiedName).stream()
            .filter(symbol -> symbol.simpleName().equals(simpleName))
            .toList();
    }

    private static void index(
        Map<String, List<JavaProjectSemanticIndex.SymbolDescriptor>> index,
        String key,
        JavaProjectSemanticIndex.SymbolDescriptor descriptor
    ) {
        if (key == null || key.isBlank())
            return;

        index.computeIfAbsent(key, $ -> new ArrayList<>()).add(descriptor);
    }

    private static List<JavaProjectSemanticIndex.SymbolDescriptor> lookup(
        Map<String, List<JavaProjectSemanticIndex.SymbolDescriptor>> index,
        String key
    ) {
        if (key == null || key.isBlank())
            return List.of();

        return index.getOrDefault(key.trim(), List.of());
    }

    private static <T> Map<String, List<T>> copyListMap(Map<String, List<T>> original) {
        Map<String, List<T>> copy = new LinkedHashMap<>(original.size());
        for (Map.Entry<String, List<T>> entry : original.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }

        return Map.copyOf(copy);
    }

    private static SymbolKind toTypeKind(ClassStub stub) {
        int modifiers = stub.modifiers();
        if ((modifiers & Opcodes.ACC_ANNOTATION) != 0)
            return SymbolKind.ANNOTATION;
        if ((modifiers & Opcodes.ACC_ENUM) != 0)
            return SymbolKind.ENUM;
        if ((modifiers & Opcodes.ACC_RECORD) != 0)
            return SymbolKind.RECORD;
        if ((modifiers & Opcodes.ACC_INTERFACE) != 0)
            return SymbolKind.INTERFACE;
        return SymbolKind.CLASS;
    }

    private static String renderSignature(MethodStub method) {
        return renderSignature(method.parameters().stream().map(Parameter::type).toList());
    }

    private static String renderSignature(ConstructorStub constructor) {
        return renderSignature(constructor.parameters().stream().map(Parameter::type).toList());
    }

    private static String renderSignature(List<dev.railroadide.railroad.ide.classparser.Type> parameterTypes) {
        StringBuilder builder = new StringBuilder("(");
        for (int index = 0; index < parameterTypes.size(); index++) {
            if (index > 0)
                builder.append(", ");
            builder.append(renderType(parameterTypes.get(index)));
        }
        builder.append(')');
        return builder.toString();
    }

    private static String renderType(dev.railroadide.railroad.ide.classparser.Type type) {
        return switch (type) {
            case dev.railroadide.railroad.ide.classparser.Type.PrimitiveType primitive -> primitive.name();
            case dev.railroadide.railroad.ide.classparser.Type.ArrayType array -> renderType(array.componentType()) + "[]";
            case dev.railroadide.railroad.ide.classparser.Type.ClassType clazz -> clazz.name();
            case dev.railroadide.railroad.ide.classparser.Type.TypeVariable variable -> variable.name();
            case dev.railroadide.railroad.ide.classparser.Type.WildcardType wildcard ->
                wildcard.bound() == null ? "?" : "? " + renderType(wildcard.bound());
        };
    }
}
