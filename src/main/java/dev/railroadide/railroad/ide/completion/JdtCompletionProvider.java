package dev.railroadide.railroad.ide.completion;

import org.eclipse.jdt.core.dom.*;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Default completion provider using Eclipse JDT to resolve types and members.
 */
public record JdtCompletionProvider(Path filePath, String[] systemModulePaths) implements CompletionProvider {
    @Override
    public @Nullable CompletionResult compute(String document, int triggerAt) {
        if (document == null || document.isEmpty() || triggerAt < 0 || triggerAt >= document.length())
            return null;

        if (document.charAt(triggerAt) != '.')
            return null;

        Pair range = findIdentifierRangeBeforeDot(document, triggerAt);
        if (range == null)
            return null;

        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setSource(document.toCharArray());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        parser.setStatementsRecovery(true);
        parser.setUnitName(filePath.getFileName().toString());
        parser.setEnvironment(systemModulePaths, null, null, false);

        CompilationUnit unit = (CompilationUnit) parser.createAST(null);
        NodeFinder finder = new NodeFinder(unit, range.start(), Math.max(1, range.length()));
        ASTNode node = finder.getCoveredNode();
        if (node == null) {
            node = finder.getCoveringNode();
        }

        CompletionTarget target = null;
        ASTNode cursor = node;
        while (cursor != null && target == null) {
            if (cursor instanceof ExpressionStatement statement) {
                cursor = statement.getExpression();
            }

            target = resolveCompletionTarget(cursor);
            cursor = cursor != null ? cursor.getParent() : null;
        }

        if (target == null || target.type() == null)
            return null;

        List<CompletionItem> suggestions = collectCompletionSuggestions(unit, target);
        if (suggestions.isEmpty())
            return null;

        return new CompletionResult(triggerAt, suggestions);
    }

    private CompletionTarget resolveCompletionTarget(ASTNode node) {
        if (node == null)
            return null;

        if (node instanceof Name name) {
            IBinding binding = name.resolveBinding();
            if (binding instanceof ITypeBinding typeBinding)
                return new CompletionTarget(normalizeType(typeBinding), true);

            if (binding instanceof IVariableBinding variableBinding) {
                ITypeBinding type = normalizeType(variableBinding.getType());
                if (type != null)
                    return new CompletionTarget(type, false);
            }

            if (binding instanceof IMethodBinding methodBinding) {
                ITypeBinding type = normalizeType(methodBinding.getReturnType());
                if (type != null)
                    return new CompletionTarget(type, false);
            }
        }

        if (node instanceof Expression expression) {
            ITypeBinding type = normalizeType(expression.resolveTypeBinding());
            if (type != null)
                return new CompletionTarget(type, false);
        }

        return null;
    }

    private ITypeBinding normalizeType(ITypeBinding binding) {
        if (binding == null)
            return null;

        if (binding.isAnonymous()) {
            binding = binding.getSuperclass();
        }

        if (binding == null)
            return null;

        if (binding.isArray() || binding.isPrimitive() || binding.isNullType())
            return binding;

        if (binding.isTypeVariable()) {
            ITypeBinding erasure = binding.getErasure();
            return erasure != null ? erasure : binding;
        }

        ITypeBinding declaration = binding.getTypeDeclaration();
        return declaration != null ? declaration : binding;
    }

    private List<CompletionItem> collectCompletionSuggestions(CompilationUnit unit, CompletionTarget target) {
        LinkedHashSet<CompletionItem> results = new LinkedHashSet<>();
        String currentPackage = extractPackageName(unit);
        Set<String> visited = ConcurrentHashMap.newKeySet();
        addMembers(target.type(), target.staticContext(), currentPackage, results, visited);
        return new ArrayList<>(results);
    }

    private String extractPackageName(CompilationUnit unit) {
        if (unit == null)
            return "";

        PackageDeclaration declaration = unit.getPackage();
        if (declaration == null || declaration.getName() == null)
            return "";

        return declaration.getName().getFullyQualifiedName();
    }

    private void addMembers(ITypeBinding type, boolean staticContext, String currentPackage,
                            LinkedHashSet<CompletionItem> results, Set<String> visited) {
        if (type == null)
            return;

        ITypeBinding working = type;
        if (!working.isArray() && !working.isPrimitive()) {
            working = working.getErasure();
        }

        String key = working.getKey();
        if (key == null) {
            key = working.getQualifiedName();
        }

        if (key == null || !visited.add(key))
            return;

        if (working.isArray()) {
            results.add(new CompletionItem("length", "length : int"));
            addMembers(working.getSuperclass(), staticContext, currentPackage, results, visited);
            return;
        }

        if (working.isPrimitive())
            return;

        for (IVariableBinding field : working.getDeclaredFields()) {
            if (field.isSynthetic())
                continue;

            int modifiers = field.getModifiers();
            boolean isStatic = Modifier.isStatic(modifiers);
            if (staticContext) {
                if (!isStatic)
                    continue;
            } else if (isStatic)
                continue;

            if (!isAccessible(modifiers, field.getDeclaringClass(), currentPackage))
                continue;

            results.add(createFieldItem(field));
        }

        for (IMethodBinding method : working.getDeclaredMethods()) {
            if (method.isConstructor() || method.isSynthetic())
                continue;

            int modifiers = method.getModifiers();
            boolean isStatic = Modifier.isStatic(modifiers);
            if (staticContext) {
                if (!isStatic)
                    continue;
            } else if (isStatic)
                continue;

            if (!isAccessible(modifiers, method.getDeclaringClass(), currentPackage))
                continue;

            results.add(createMethodItem(method));
        }

        if (staticContext) {
            for (ITypeBinding nested : working.getDeclaredTypes()) {
                if (nested == null || nested.getName() == null || nested.getName().isBlank())
                    continue;

                int modifiers = nested.getModifiers();
                if (!Modifier.isStatic(modifiers))
                    continue;

                if (!isAccessible(modifiers, nested, currentPackage))
                    continue;

                results.add(createTypeItem(nested));
            }
        }

        addMembers(working.getSuperclass(), staticContext, currentPackage, results, visited);
        for (ITypeBinding iface : working.getInterfaces()) {
            addMembers(iface, staticContext, currentPackage, results, visited);
        }
    }

    private boolean isAccessible(int modifiers, ITypeBinding declaringType, String currentPackage) {
        if (Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers))
            return true;

        if (Modifier.isPrivate(modifiers))
            return false;

        String declaringPackage = declaringType != null ? packageName(declaringType) : "";
        return declaringPackage.equals(currentPackage);
    }

    private CompletionItem createFieldItem(IVariableBinding field) {
        String name = field.getName();
        String type = renderType(field.getType());
        String prefix = Modifier.isStatic(field.getModifiers()) ? "static " : "";
        return new CompletionItem(name, prefix + name + " : " + type);
    }

    private CompletionItem createMethodItem(IMethodBinding method) {
        String name = method.getName();
        String prefix = Modifier.isStatic(method.getModifiers()) ? "static " : "";
        String parameters = Arrays.stream(method.getParameterTypes())
            .map(JdtCompletionProvider::renderType)
            .collect(Collectors.joining(", "));
        String returnType = renderType(method.getReturnType());
        return new CompletionItem(name, prefix + name + "(" + parameters + ") : " + returnType);
    }

    private CompletionItem createTypeItem(ITypeBinding type) {
        String name = type.getName();
        String prefix = Modifier.isStatic(type.getModifiers()) ? "static " : "";
        return new CompletionItem(name, prefix + name + " (type)");
    }

    public static String renderType(ITypeBinding type) {
        if (type == null)
            return "?";

        if (type.isArray())
            return renderType(type.getComponentType()) + "[]";

        if (type.isPrimitive() || type.isNullType())
            return type.getName();

        if (type.isTypeVariable()) {
            ITypeBinding erasure = type.getErasure();
            if (erasure != null)
                return renderType(erasure);
        }

        ITypeBinding declaration = type.getTypeDeclaration();
        String qualified = declaration != null ? declaration.getQualifiedName() : type.getQualifiedName();
        if (qualified != null && !qualified.isBlank())
            return qualified;

        String name = type.getName();
        return name != null && !name.isBlank() ? name : "?";
    }

    private String packageName(ITypeBinding type) {
        if (type == null)
            return "";

        IPackageBinding pkg = type.getPackage();
        if (pkg == null)
            return "";

        String name = pkg.getName();
        return name != null ? name : "";
    }

    private Pair findIdentifierRangeBeforeDot(String text, int dotPosition) {
        int start = dotPosition - 1;
        while (start >= 0 && Character.isJavaIdentifierPart(text.charAt(start))) {
            start--;
        }

        start++;
        if (start > dotPosition)
            return null;

        return new Pair(start, dotPosition - start);
    }

    private record CompletionTarget(ITypeBinding type, boolean staticContext) {
    }

    private record Pair(int start, int length) {
    }
}
