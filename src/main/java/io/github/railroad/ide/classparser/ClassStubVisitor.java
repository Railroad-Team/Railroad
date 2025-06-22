package io.github.railroad.ide.classparser;

import io.github.railroad.ide.classparser.stub.*;
import org.objectweb.asm.*;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

import java.util.*;
import java.util.function.Consumer;

public class ClassStubVisitor extends ClassVisitor {
    private String className;
    private String packageName;
    private int modifiers;
    private List<TypeParameter> typeParameters = new ArrayList<>();
    private Type superClass;
    private List<Type> interfaces;
    private final List<FieldStub> fields = new ArrayList<>();
    private final List<MethodStub> methods = new ArrayList<>();
    private final List<ConstructorStub> constructors = new ArrayList<>();
    private final List<AnnotationStub> annotations = new ArrayList<>();

    public ClassStubVisitor() {
        super(Opcodes.ASM9);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        int lastSlashIndex = name.lastIndexOf('/');
        this.className = (lastSlashIndex == -1) ? name : name.substring(lastSlashIndex + 1);
        this.packageName = (lastSlashIndex == -1) ? "" : name.substring(0, lastSlashIndex).replace('/', '.');
        this.modifiers = access;

        if (signature != null) {
            var signatureVisitor = new ClassSignatureVisitor();
            new SignatureReader(signature).accept(signatureVisitor);
            this.typeParameters = signatureVisitor.typeParameters;
            this.superClass = signatureVisitor.superClass;
            this.interfaces = signatureVisitor.interfaces;
        } else {
            this.superClass = (superName == null) ? null : Type.fromAsmType(org.objectweb.asm.Type.getObjectType(superName));
            this.interfaces = Arrays.stream(interfaces)
                    .map(org.objectweb.asm.Type::getObjectType)
                    .map(Type::fromAsmType)
                    .toList();
        }
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        String annotationName = org.objectweb.asm.Type.getType(descriptor).getClassName();
        return new AnnotationStubVisitor(annotationName, this.annotations::add);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        Type fieldType = Type.fromAsmType(org.objectweb.asm.Type.getType(descriptor));
        List<AnnotationStub> fieldAnnotations = new ArrayList<>();
        return new FieldVisitor(Opcodes.ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                String annotationName = org.objectweb.asm.Type.getType(descriptor).getClassName();
                return new AnnotationStubVisitor(annotationName, fieldAnnotations::add);
            }

            @Override
            public void visitEnd() {
                var fieldStub = new FieldStub(name, fieldType, access, fieldAnnotations);
                ClassStubVisitor.this.fields.add(fieldStub);
            }
        };
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (name.equals("<clinit>")) {
            return null; // Skip static initializer
        }

        List<Type> parameterTypes;
        Type returnType;
        List<TypeParameter> methodTypeParameters = new ArrayList<>();
        if (signature != null) {
            var signatureVisitor = new MethodSignatureVisitor();
            new SignatureReader(signature).accept(signatureVisitor);
            methodTypeParameters = signatureVisitor.typeParameters;
            parameterTypes = signatureVisitor.parameterTypes;
            returnType = signatureVisitor.returnType;
        } else {
            parameterTypes = Arrays.stream(org.objectweb.asm.Type.getArgumentTypes(descriptor))
                    .map(Type::fromAsmType)
                    .toList();

            returnType = Type.fromAsmType(org.objectweb.asm.Type.getReturnType(descriptor));
        }

        List<String> parameterNames = new ArrayList<>();
        List<List<AnnotationStub>> parameterAnnotations = new ArrayList<>(parameterTypes.size());
        for (int i = 0; i < parameterTypes.size(); i++) {
            parameterNames.add("arg" + i);
            parameterAnnotations.add(new ArrayList<>());
        }

        List<AnnotationStub> methodAnnotations = new ArrayList<>();
        return new MethodStubVisitor(access, name, parameterTypes, returnType, parameterNames, parameterAnnotations, methodAnnotations, methodTypeParameters);
    }

    public ClassStub createClassStub() {
        if (className == null) {
            return null; // Class name is not available
        }

        //    String packageName,
        //    String className,
        //    List<TypeParameter> typeParameters,
        //    Type superClass,
        //    List<Type> interfaces,
        //    List<FieldStub> fields,
        //    List<MethodStub> methods,
        //    List<ConstructorStub> constructors,
        //    int modifiers,
        //    List<AnnotationStub> annotations
        return new ClassStub(
                packageName,
                className,
                typeParameters,
                superClass,
                interfaces,
                fields,
                methods,
                constructors,
                modifiers,
                annotations
        );
    }

    private static class AnnotationStubVisitor extends AnnotationVisitor {
        private final String type;
        private final Map<String, Object> values = new HashMap<>();
        private final Consumer<AnnotationStub> onFinish;

        public AnnotationStubVisitor(String type, Consumer<AnnotationStub> onFinish) {
            super(Opcodes.ASM9);
            this.type = type;
            this.onFinish = onFinish;
        }

        @Override
        public void visit(String name, Object value) {
            this.values.put(name, value);
        }

        @Override
        public void visitEnd() {
            var annotationStub = new AnnotationStub(type, values);
            onFinish.accept(annotationStub);
        }
    }

    private static class ClassSignatureVisitor extends SignatureVisitor {
        private final List<TypeParameter> typeParameters = new ArrayList<>();
        private Type superClass;
        private final List<Type> interfaces = new ArrayList<>();

        private TypeParameter currentTypeParameter;

        public ClassSignatureVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visitFormalTypeParameter(String name) {
            this.currentTypeParameter = new TypeParameter(name, new ArrayList<>());
            this.typeParameters.add(this.currentTypeParameter);
        }

        @Override
        public SignatureVisitor visitClassBound() {
            return new TypeSignatureVisitor(this.currentTypeParameter.bounds()::add);
        }

        @Override
        public SignatureVisitor visitInterfaceBound() {
            return new TypeSignatureVisitor(this.currentTypeParameter.bounds()::add);
        }

        @Override
        public SignatureVisitor visitSuperclass() {
            return new TypeSignatureVisitor(type -> ClassSignatureVisitor.this.superClass = type);
        }

        @Override
        public SignatureVisitor visitInterface() {
            return new TypeSignatureVisitor(ClassSignatureVisitor.this.interfaces::add);
        }
    }

    private static class TypeSignatureVisitor extends SignatureVisitor {
        private final Consumer<Type> onFinish;
        private Type result;
        private final Deque<Type.ClassType> typeStack = new ArrayDeque<>();

        public TypeSignatureVisitor(Consumer<Type> onFinish) {
            super(Opcodes.ASM9);
            this.onFinish = onFinish;
        }

        @Override
        public void visitClassType(String name) {
            String className = name.replace('/', '.');
            var classType = new Type.ClassType(className, new ArrayList<>());
            if (this.result == null) {
                this.result = classType;
            } else {
                Type.ClassType parent = this.typeStack.peek();
                if (parent != null) {
                    parent.typeArguments().add(classType);
                }
            }

            this.typeStack.push(classType);
        }

        @Override
        public void visitTypeVariable(String name) {
            var typeVariable = new Type.TypeVariable(name);
            if (this.result == null) {
                this.result = typeVariable;
            } else {
                Type.ClassType parent = this.typeStack.peek();
                if (parent != null) {
                    parent.typeArguments().add(typeVariable);
                }
            }
        }

        @Override
        public SignatureVisitor visitTypeArgument(char wildcard) {
            return new TypeSignatureVisitor(type -> {
                Type argType = switch (wildcard) {
                    case SignatureVisitor.EXTENDS -> new Type.WildcardType(type, true);
                    case SignatureVisitor.SUPER -> new Type.WildcardType(type, false);
                    default -> type;
                };

                Type.ClassType parent = this.typeStack.peek();
                if (parent != null) {
                    parent.typeArguments().add(argType);
                }
            });
        }

        @Override
        public void visitEnd() {
            if (!this.typeStack.isEmpty()) {
                this.typeStack.pop();
            }

            if (this.typeStack.isEmpty() && this.onFinish != null && this.result != null) {
                this.onFinish.accept(this.result);
            }
        }
    }

    private static class MethodSignatureVisitor extends SignatureVisitor {
        private final List<TypeParameter> typeParameters = new ArrayList<>();
        private final List<Type> parameterTypes = new ArrayList<>();
        private Type returnType;
        private TypeParameter currentTypeParameter;

        public MethodSignatureVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visitFormalTypeParameter(String name) {
            currentTypeParameter = new TypeParameter(name, new ArrayList<>());
            typeParameters.add(currentTypeParameter);
        }

        @Override
        public SignatureVisitor visitClassBound() {
            return new TypeSignatureVisitor(currentTypeParameter.bounds()::add);
        }

        @Override
        public SignatureVisitor visitInterfaceBound() {
            return new TypeSignatureVisitor(currentTypeParameter.bounds()::add);
        }

        @Override
        public SignatureVisitor visitParameterType() {
            return new TypeSignatureVisitor(parameterTypes::add);
        }

        @Override
        public SignatureVisitor visitReturnType() {
            return new TypeSignatureVisitor(type -> returnType = type);
        }
    }

    private class MethodStubVisitor extends MethodVisitor {
        private final int access;
        private final String name;
        private final List<Type> parameterTypes;
        private final Type returnType;
        private final List<String> parameterNames;
        private final List<List<AnnotationStub>> parameterAnnotations;
        private final List<AnnotationStub> methodAnnotations;
        private final List<TypeParameter> finalMethodTypeParameters;
        private int parameterIndex = 0;

        public MethodStubVisitor(int access, String name, List<Type> parameterTypes, Type returnType, List<String> parameterNames, List<List<AnnotationStub>> parameterAnnotations, List<AnnotationStub> methodAnnotations, List<TypeParameter> finalMethodTypeParameters) {
            super(Opcodes.ASM9);
            this.access = access;
            this.name = name;
            this.parameterTypes = parameterTypes;
            this.returnType = returnType;
            this.parameterNames = parameterNames;
            this.parameterAnnotations = parameterAnnotations;
            this.methodAnnotations = methodAnnotations;
            this.finalMethodTypeParameters = finalMethodTypeParameters;
        }

        @Override
        public void visitParameter(String name, int access) {
            if (parameterIndex < parameterNames.size()) {
                parameterNames.set(parameterIndex, name);
                parameterIndex++;
            }
        }

        @Override
        public AnnotationVisitor visitParameterAnnotation(int parameterIndex, String descriptor, boolean visible) {
            if (parameterIndex < parameterAnnotations.size()) {
                String annotationName = org.objectweb.asm.Type.getType(descriptor).getClassName();
                return new AnnotationStubVisitor(annotationName, parameterAnnotations.get(parameterIndex)::add);
            }

            return null; // Ignore if parameter index is out of bounds
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            String annotationName = org.objectweb.asm.Type.getType(descriptor).getClassName();
            return new AnnotationStubVisitor(annotationName, methodAnnotations::add);
        }

        @Override
        public void visitEnd() {
            List<Parameter> parameters = new ArrayList<>();
            for (int i = 0; i < parameterTypes.size(); i++) {
                String name = parameterNames.get(i);
                Type type = parameterTypes.get(i);
                List<AnnotationStub> annotations = parameterAnnotations.get(i);
                parameters.add(new Parameter(name, type, annotations));
            }

            if (name.equals("<init>")) {
                var constructorStub = new ConstructorStub(
                        parameters, access, methodAnnotations, finalMethodTypeParameters);
                ClassStubVisitor.this.constructors.add(constructorStub);
            } else {
                var methodStub = new MethodStub(
                        name, returnType, parameters, access, methodAnnotations, finalMethodTypeParameters);
                ClassStubVisitor.this.methods.add(methodStub);
            }
        }
    }
}