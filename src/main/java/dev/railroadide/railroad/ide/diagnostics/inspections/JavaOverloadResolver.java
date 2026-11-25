package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxKinds;
import dev.railroadide.railroad.ide.sst.impl.java.JavaTokenType;
import dev.railroadide.railroad.ide.sst.java.api.JavaTypes;
import dev.railroadide.railroad.ide.sst.semantic.api.Symbol;
import dev.railroadide.railroad.ide.sst.semantic.api.Type;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext.MethodDescriptor;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Chooses among the overloads of a method invocation as JLS 15.12.2 does. Candidates are the member methods of the
 * class to search, and they are tried by strict invocation, then loose invocation, then variable arity invocation.
 * The most specific method of the first phase with an applicable method wins.
 * <p>
 * Type inference, poly expressions and unknown types are not modelled. Whenever the outcome depends on one of them,
 * the resolution is {@link Outcome#UNDETERMINED} rather than a guess, so the other outcomes can be reported as errors.
 */
public final class JavaOverloadResolver {
    private static final Resolution UNDETERMINED = new Resolution(Outcome.UNDETERMINED, List.of());
    private static final Resolution ERRONEOUS = new Resolution(Outcome.ERRONEOUS_ARGUMENTS, List.of());
    private static final String OBJECT = "java.lang.Object";
    private static final Set<String> ARRAY_SUPERTYPES = Set.of(OBJECT, "java.lang.Cloneable",
        "java.io.Serializable");
    /**
     * Source enums do not declare these implicit methods, so their overloads are unknown.
     */
    private static final Set<String> IMPLICIT_ENUM_METHODS = Set.of("values", "valueOf");
    private static final Set<String> POLY_EXPRESSION_KINDS = Set.of(
        JavaSyntaxKinds.LAMBDA_EXPRESSION.id(),
        JavaSyntaxKinds.METHOD_REFERENCE_EXPRESSION.id(),
        JavaSyntaxKinds.CONDITIONAL_EXPRESSION.id(),
        JavaSyntaxKinds.SWITCH_EXPRESSION.id());
    private static final Map<String, Set<String>> PRIMITIVE_WIDENING = Map.of(
        "byte", Set.of("short", "int", "long", "float", "double"),
        "short", Set.of("int", "long", "float", "double"),
        "char", Set.of("int", "long", "float", "double"),
        "int", Set.of("long", "float", "double"),
        "long", Set.of("float", "double"),
        "float", Set.of("double"));
    private static final Map<String, String> BOXED_TYPES = Map.of(
        "boolean", "java.lang.Boolean",
        "byte", "java.lang.Byte",
        "short", "java.lang.Short",
        "char", "java.lang.Character",
        "int", "java.lang.Integer",
        "long", "java.lang.Long",
        "float", "java.lang.Float",
        "double", "java.lang.Double");

    private final JavaRuleContext context;

    private JavaOverloadResolver(JavaRuleContext context) {
        this.context = context;
    }

    /**
     * Resolves the overloads of a method invocation.
     *
     * @param context the rule context of the invocation's file
     * @param invocation the method invocation node
     * @return the outcome, with the chosen method, the ambiguous methods or every inapplicable candidate
     */
    public static Resolution resolve(JavaRuleContext context, SyntaxNode invocation) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(invocation, "invocation");
        return new JavaOverloadResolver(context).resolveInvocation(invocation);
    }

    private Resolution resolveInvocation(SyntaxNode invocation) {
        if (!JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id().equals(invocation.kind().id())
            || hasExplicitTypeArguments(invocation))
            return UNDETERMINED;

        SyntaxNode selector = context.selectorNameNode(invocation);
        String name = selector == null
            ? JavaSemanticAnalyzer.identifierBeforeChildKind(invocation, JavaSyntaxKinds.ARGUMENT_LIST.id())
            : context.lastIdentifierLikeTokenText(selector);
        if (name == null || name.isBlank() || IMPLICIT_ENUM_METHODS.contains(name))
            return UNDETERMINED;

        SyntaxNode argumentList = context.directChild(invocation, JavaSyntaxKinds.ARGUMENT_LIST.id());
        if (argumentList != null && hasUnresolvedName(argumentList))
            return ERRONEOUS;

        List<Argument> arguments = arguments(argumentList);
        List<MethodDescriptor> members = arguments == null ? null : memberMethods(invocation, name);
        if (members == null)
            return UNDETERMINED;

        List<MethodDescriptor> accessible = members.stream()
            .filter(method -> isAccessible(method, invocation))
            .toList();
        if (accessible.isEmpty())
            return UNDETERMINED;

        Resolution resolution = choose(accessible, arguments);
        // javac reports inaccessible candidates as access errors instead.
        return resolution.outcome() == Outcome.INAPPLICABLE && accessible.size() != members.size()
            ? UNDETERMINED
            : resolution;
    }

    private boolean hasExplicitTypeArguments(SyntaxNode invocation) {
        for (SyntaxNode child : invocation.children()) {
            String kindId = child.kind().id();
            if (JavaSyntaxKinds.ARGUMENT_LIST.id().equals(kindId))
                return false;

            if (JavaSyntaxKinds.TYPE_ARGUMENTS.id().equals(kindId)
                || context.directChild(child, JavaSyntaxKinds.TYPE_ARGUMENTS.id()) != null)
                return true;
        }

        return false;
    }

    private @Nullable List<Argument> arguments(@Nullable SyntaxNode argumentList) {
        if (argumentList == null)
            return List.of();

        List<Argument> arguments = new ArrayList<>();
        for (SyntaxNode child : argumentList.children()) {
            if (!context.isExpressionNode(child))
                continue;

            Argument argument = argument(child);
            if (argument == null)
                return null;

            arguments.add(argument);
        }

        return List.copyOf(arguments);
    }

    private @Nullable Argument argument(SyntaxNode expression) {
        SyntaxNode unwrapped = context.unwrapTransparentExpression(expression);
        if (unwrapped == null || POLY_EXPRESSION_KINDS.contains(unwrapped.kind().id()))
            return null;

        String kindId = unwrapped.kind().id();
        if (JavaSyntaxKinds.LITERAL_EXPRESSION.id().equals(kindId)
            && context.hasTokenKind(unwrapped, JavaTokenType.NULL_LITERAL))
            return new Argument(new JavaTypes.UnknownType("null"), true);

        if (JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id().equals(kindId) && mayInferResult(unwrapped))
            return null;

        if (JavaSyntaxKinds.CLASS_INSTANCE_CREATION_EXPRESSION.id().equals(kindId)
            && containsKind(unwrapped, JavaSyntaxKinds.DIAMOND_TYPE_ARGUMENTS.id()))
            return null;

        if (hasImplicitLambdaParameter(unwrapped))
            return null;

        Type type = context.inferredType(expression).orElse(null);
        return type != null && isProperType(type) ? new Argument(type, false) : null;
    }

    /**
     * An argument naming something unresolved is erroneous, and javac then reports only the name.
     */
    private boolean hasUnresolvedName(SyntaxNode node) {
        return anyName(node, symbol -> symbol == null);
    }

    /**
     * An implicitly typed lambda parameter takes its type from an overload choice the analyzer may not share.
     */
    private boolean hasImplicitLambdaParameter(SyntaxNode node) {
        return anyName(node, symbol -> {
            SyntaxNode declaration = symbol == null ? null : symbol.declaration().orElse(null);
            return declaration != null && JavaSyntaxKinds.LAMBDA_PARAMETER.id().equals(declaration.kind().id())
                && context.directChild(declaration, JavaSyntaxKinds.TYPE_REFERENCE.id()) == null;
        });
    }

    /**
     * Tests the simple names of an expression, outside nested lambda bodies and class bodies.
     */
    private boolean anyName(SyntaxNode node, Predicate<@Nullable Symbol> test) {
        String kindId = node.kind().id();
        if (JavaSyntaxKinds.LAMBDA_EXPRESSION.id().equals(kindId)
            || JavaSyntaxKinds.ANONYMOUS_CLASS_BODY.id().equals(kindId))
            return false;

        if (JavaSyntaxKinds.NAME_EXPRESSION.id().equals(kindId) && !context.isSelectorNameExpression(node)
            && test.test(context.resolvedSymbol(node).orElse(null)))
            return true;

        for (SyntaxNode child : node.children()) {
            if (anyName(child, test))
                return true;
        }

        return false;
    }

    /**
     * The result of a generic method depends on the target type, which makes the invocation a poly expression.
     */
    private boolean mayInferResult(SyntaxNode invocation) {
        Symbol method = context.resolvedSymbol(invocation).orElse(null);
        String owner = method == null ? null : context.ownerQualifiedName(method).orElse(null);
        if (owner == null)
            return true;

        List<MethodDescriptor> overloads = new ArrayList<>(context.methodDescriptorsForType(owner));
        overloads.addAll(context.inheritedMethodDescriptors(owner));
        List<MethodDescriptor> named = overloads.stream()
            .filter(candidate -> candidate.name().equals(method.simpleName()))
            .toList();
        return named.isEmpty() || named.stream().anyMatch(candidate -> !candidate.typeParameters().isEmpty());
    }

    private boolean isProperType(Type type) {
        return switch (type) {
            case JavaTypes.ArrayType array -> isProperType(array.componentType());
            default -> type.kind() == JavaTypes.Kind.PRIMITIVE || type.kind() == JavaTypes.Kind.DECLARED;
        };
    }

    /**
     * Finds the member methods of the class to search (JLS 15.12.1): the receiver's type, or for an unqualified name,
     * the innermost enclosing type with a member method of that name.
     */
    private @Nullable List<MethodDescriptor> memberMethods(SyntaxNode invocation, String name) {
        SyntaxNode receiver = context.unwrapTransparentExpression(context.invocationReceiver(invocation));
        if (receiver != null) {
            String owner = receiverType(receiver);
            if (owner == null || !context.isTypeHierarchyKnown(owner))
                return null;

            List<MethodDescriptor> members = members(owner, name);
            return members.isEmpty() ? null : members;
        }

        for (SyntaxNode current = invocation.parent().orElse(null); current != null; current = current.parent()
            .orElse(null)) {
            String kindId = current.kind().id();
            if (JavaSyntaxKinds.ANONYMOUS_CLASS_BODY.id().equals(kindId)
                || JavaSyntaxKinds.ENUM_CONSTANT.id().equals(kindId))
                return null;

            Symbol declared = context.declaredSymbol(current).orElse(null);
            if (declared == null || !context.isTypeSymbol(declared.kind()))
                continue;

            String owner = declared.qualifiedName().orElse(null);
            if (owner == null || !context.isTypeHierarchyKnown(owner))
                return null;

            List<MethodDescriptor> members = members(owner, name);
            if (!members.isEmpty())
                return members;
        }

        return null;
    }

    private @Nullable String receiverType(SyntaxNode receiver) {
        Symbol symbol = context.resolvedSymbol(receiver).orElse(null);
        if (symbol != null && context.isTypeSymbol(symbol.kind()))
            return symbol.qualifiedName().orElse(null);

        Type type = context.inferredType(receiver).orElse(null);
        return type == null || type.kind() != JavaTypes.Kind.DECLARED
            ? null
            : context.resolveQualifiedTypeName(type.displayName());
    }

    private List<MethodDescriptor> members(String owner, String name) {
        Map<String, MethodDescriptor> bySignature = new LinkedHashMap<>();
        for (MethodDescriptor method : context.methodDescriptorsForType(owner)) {
            if (method.name().equals(name)) {
                bySignature.putIfAbsent(signature(method), method);
            }
        }

        for (MethodDescriptor method : context.inheritedMethodDescriptors(owner)) {
            if (method.name().equals(name) && isInherited(method, owner)) {
                bySignature.putIfAbsent(signature(method), method);
            }
        }

        if (context.isInterfaceType(owner)) {
            for (MethodDescriptor method : context.methodDescriptorsForType(OBJECT)) {
                if (method.name().equals(name) && Modifier.isPublic(method.modifiers())) {
                    bySignature.putIfAbsent(signature(method), method);
                }
            }
        }

        return List.copyOf(bySignature.values());
    }

    private boolean isInherited(MethodDescriptor method, String owner) {
        if (Modifier.isPrivate(method.modifiers()))
            return false;

        return !hasPackageAccess(method)
            || context.packageNameOfType(method.ownerQualifiedName()).equals(context.packageNameOfType(owner));
    }

    private boolean isAccessible(MethodDescriptor method, SyntaxNode usageSite) {
        if (Modifier.isPrivate(method.modifiers())) {
            String currentTopLevel = context.topLevelEnclosingTypeSymbol(usageSite)
                .flatMap(Symbol::qualifiedName)
                .orElse(null);
            return Objects.equals(context.topLevelTypeQualifiedName(method.ownerQualifiedName()), currentTopLevel);
        }

        return !hasPackageAccess(method)
            || context.packageNameOfType(method.ownerQualifiedName()).equals(context.currentPackageName());
    }

    private boolean hasPackageAccess(MethodDescriptor method) {
        int modifiers = method.modifiers();
        return !Modifier.isPublic(modifiers) && !Modifier.isProtected(modifiers) && !Modifier.isPrivate(modifiers)
            && !context.isInterfaceType(method.ownerQualifiedName());
    }

    private Resolution choose(List<MethodDescriptor> candidates, List<Argument> arguments) {
        for (Phase phase : Phase.values()) {
            List<MethodDescriptor> applicable = new ArrayList<>();
            for (MethodDescriptor candidate : candidates) {
                if (phase == Phase.VARIABLE_ARITY && !context.isVariableArityMethod(candidate))
                    continue;

                Fit fit = applicability(candidate, arguments, phase);
                if (fit == Fit.MAYBE)
                    return UNDETERMINED;

                if (fit == Fit.YES) {
                    applicable.add(candidate);
                }
            }

            if (!applicable.isEmpty())
                return mostSpecific(applicable, arguments.size(), phase);
        }

        return new Resolution(Outcome.INAPPLICABLE, candidates);
    }

    private Fit applicability(MethodDescriptor method, List<Argument> arguments, Phase phase) {
        List<Type> parameters = method.parameterTypes();
        Fit fit = Fit.YES;
        if (phase != Phase.VARIABLE_ARITY) {
            if (parameters.size() != arguments.size())
                return Fit.NO;

            for (int index = 0; index < parameters.size(); index++) {
                fit = fit.and(parameterFit(method, parameters.get(index), arguments.get(index), phase == Phase.LOOSE));
            }
        } else {
            if (parameters.isEmpty() || arguments.size() < parameters.size() - 1)
                return Fit.NO;

            if (!(parameters.getLast() instanceof JavaTypes.ArrayType variableArity))
                return Fit.MAYBE;

            int fixed = parameters.size() - 1;
            for (int index = 0; index < arguments.size(); index++) {
                Type parameter = index < fixed ? parameters.get(index) : variableArity.componentType();
                fit = fit.and(parameterFit(method, parameter, arguments.get(index), true));
            }
        }

        // Inference may still fail to satisfy a generic method's bounds.

        return fit == Fit.YES && !method.typeParameters().isEmpty() ? Fit.MAYBE : fit;
    }

    /**
     * Inference may substitute a generic method's type variables with types this check cannot see, such as a capture
     * or a type shadowing an imported one, so only parameters free of them can rule the method out.
     */
    private Fit parameterFit(MethodDescriptor method, Type parameter, Argument argument, boolean loose) {
        Fit fit = compatible(parameter, argument, loose);
        return fit == Fit.NO && !method.typeParameters().isEmpty() && mentionsTypeVariable(parameter) ? Fit.MAYBE : fit;
    }

    private static boolean mentionsTypeVariable(Type type) {
        return switch (type) {
            case JavaTypes.ArrayType array -> mentionsTypeVariable(array.componentType());
            case JavaTypes.DeclaredType declared -> declared.typeArguments().stream()
                .anyMatch(JavaOverloadResolver::mentionsTypeVariable);
            default -> isTypeVariable(type) || type.kind() == JavaTypes.Kind.UNKNOWN;
        };
    }

    private Fit compatible(Type parameter, Argument argument, boolean loose) {
        if (argument.nullLiteral())
            return parameter.kind() == JavaTypes.Kind.PRIMITIVE ? Fit.NO : Fit.YES;

        Type source = argument.type();
        if (parameter.kind() == JavaTypes.Kind.PRIMITIVE) {
            if (source.kind() == JavaTypes.Kind.PRIMITIVE)
                return Fit.of(widens(source.displayName(), parameter.displayName()));

            if (!loose || source.kind() != JavaTypes.Kind.DECLARED)
                return Fit.NO;

            String unboxed = unboxedName(source);
            return Fit.of(unboxed != null && widens(unboxed, parameter.displayName()));
        }

        if (source.kind() == JavaTypes.Kind.PRIMITIVE) {
            if (isTypeVariable(parameter))
                return Fit.MAYBE;

            if (!loose)
                return Fit.NO;

            return subtype(new JavaTypes.DeclaredType(BOXED_TYPES.get(source.displayName()), List.of()), parameter);
        }

        return subtype(source, parameter);
    }

    private Fit subtype(Type subtype, Type supertype) {
        if (isTypeVariable(subtype) || isTypeVariable(supertype)
            || subtype.kind() == JavaTypes.Kind.UNKNOWN || supertype.kind() == JavaTypes.Kind.UNKNOWN)
            return subtype.equals(supertype) ? Fit.YES : Fit.MAYBE;

        if (subtype.kind() == JavaTypes.Kind.PRIMITIVE || supertype.kind() == JavaTypes.Kind.PRIMITIVE)
            return Fit.of(subtype.kind() == supertype.kind()
                && widens(subtype.displayName(), supertype.displayName()));

        if (subtype instanceof JavaTypes.ArrayType subArray) {
            if (supertype instanceof JavaTypes.ArrayType superArray) {
                Type subComponent = subArray.componentType();
                Type superComponent = superArray.componentType();
                if (subComponent.kind() == JavaTypes.Kind.PRIMITIVE
                    || superComponent.kind() == JavaTypes.Kind.PRIMITIVE)
                    return Fit.of(subComponent.equals(superComponent));

                return subtype(subComponent, superComponent);
            }

            String superName = qualifiedName(supertype);
            return superName == null ? Fit.MAYBE : Fit.of(ARRAY_SUPERTYPES.contains(superName));
        }

        if (supertype.kind() == JavaTypes.Kind.ARRAY)
            return Fit.NO;

        String subName = qualifiedName(subtype);
        String superName = qualifiedName(supertype);
        if (subName == null || superName == null)
            return Fit.MAYBE;

        if (OBJECT.equals(superName))
            return Fit.YES;

        if (!subName.equals(superName) && !context.isSubtype(subName, superName))
            return Fit.NO;

        if (!hasTypeArguments(supertype))
            return Fit.YES;

        return subtype.equals(supertype) ? Fit.YES : Fit.MAYBE;
    }

    private Resolution mostSpecific(List<MethodDescriptor> applicable, int argumentCount, Phase phase) {
        if (applicable.size() == 1)
            return new Resolution(Outcome.RESOLVED, applicable);

        // A generic method is more specific only if inference succeeds (JLS 18.5.4).
        if (applicable.stream().anyMatch(method -> !method.typeParameters().isEmpty()))
            return UNDETERMINED;

        int count = applicable.size();
        boolean[][] moreSpecific = new boolean[count][count];
        for (int first = 0; first < count; first++) {
            for (int second = 0; second < count; second++) {
                if (first == second)
                    continue;

                Fit fit = moreSpecific(applicable.get(first), applicable.get(second), argumentCount, phase);
                if (fit == Fit.MAYBE)
                    return UNDETERMINED;

                moreSpecific[first][second] = fit == Fit.YES;
            }
        }

        List<MethodDescriptor> maximal = new ArrayList<>();
        for (int candidate = 0; candidate < count; candidate++) {
            boolean dominated = false;
            for (int other = 0; other < count && !dominated; other++) {
                dominated = moreSpecific[other][candidate] && !moreSpecific[candidate][other];
            }

            if (!dominated) {
                maximal.add(applicable.get(candidate));
            }
        }

        if (maximal.size() == 1)
            return new Resolution(Outcome.RESOLVED, maximal);

        // Override-equivalent maximal methods are resolved by their modifiers rather than reported (JLS 15.12.2.5).
        String firstSignature = erasedParameters(maximal.getFirst());
        if (maximal.stream().allMatch(method -> erasedParameters(method).equals(firstSignature)))
            return new Resolution(Outcome.RESOLVED, maximal);

        return new Resolution(Outcome.AMBIGUOUS, List.copyOf(maximal));
    }

    private Fit moreSpecific(MethodDescriptor first, MethodDescriptor second, int argumentCount, Phase phase) {
        boolean variableArity = phase == Phase.VARIABLE_ARITY;
        List<Type> firstTypes = variableArity ? expanded(first, argumentCount + 1) : first.parameterTypes();
        List<Type> secondTypes = variableArity ? expanded(second, argumentCount + 1) : second.parameterTypes();
        Fit fit = Fit.YES;
        for (int index = 0; index < argumentCount; index++) {
            fit = fit.and(subtype(firstTypes.get(index), secondTypes.get(index)));
        }

        if (variableArity && second.parameterTypes().size() == argumentCount + 1) {
            fit = fit.and(subtype(firstTypes.get(argumentCount), secondTypes.get(argumentCount)));
        }

        return fit;
    }

    private static List<Type> expanded(MethodDescriptor method, int count) {
        List<Type> parameters = method.parameterTypes();
        int fixed = parameters.size() - 1;
        Type component = ((JavaTypes.ArrayType) parameters.getLast()).componentType();
        List<Type> types = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            types.add(index < fixed ? parameters.get(index) : component);
        }

        return types;
    }

    private String signature(MethodDescriptor method) {
        return method.name() + erasedParameters(method);
    }

    private String erasedParameters(MethodDescriptor method) {
        return method.parameterTypes().stream()
            .map(this::erasure)
            .collect(Collectors.joining(",", "(", ")"));
    }

    private String erasure(Type type) {
        if (type instanceof JavaTypes.ArrayType array)
            return erasure(array.componentType()) + "[]";

        if (type.kind() == JavaTypes.Kind.DECLARED) {
            String name = qualifiedName(type);
            return name == null ? type.displayName() : name;
        }

        return type.kind().id() + ":" + type.displayName();
    }

    private @Nullable String qualifiedName(Type type) {
        if (type.kind() != JavaTypes.Kind.DECLARED)
            return null;

        String name = context.resolveQualifiedTypeName(type.displayName());
        return name == null || !context.isTypeHierarchyKnown(name) ? null : name;
    }

    private @Nullable String unboxedName(Type type) {
        String name = qualifiedName(type);
        for (Map.Entry<String, String> entry : BOXED_TYPES.entrySet()) {
            if (entry.getValue().equals(name))
                return entry.getKey();
        }

        return null;
    }

    private static boolean widens(String from, String to) {
        return from.equals(to) || PRIMITIVE_WIDENING.getOrDefault(from, Set.of()).contains(to);
    }

    private static boolean isTypeVariable(Type type) {
        return type.kind() == JavaTypes.Kind.TYPE_VARIABLE || type.kind() == JavaTypes.Kind.WILDCARD;
    }

    private static boolean hasTypeArguments(Type type) {
        return type instanceof JavaTypes.DeclaredType declared
            && (!declared.typeArguments().isEmpty() || declared.displayName().contains("<"));
    }

    private static boolean containsKind(SyntaxNode node, String kindId) {
        if (kindId.equals(node.kind().id()))
            return true;

        for (SyntaxNode child : node.children()) {
            if (containsKind(child, kindId))
                return true;
        }

        return false;
    }

    /**
     * How an overload resolution ended.
     */
    public enum Outcome {
        /**
         * The outcome depends on something the resolver does not model.
         */
        UNDETERMINED,
        /**
         * A most specific method was found.
         */
        RESOLVED,
        /**
         * Several applicable methods are maximally specific.
         */
        AMBIGUOUS,
        /**
         * No candidate is applicable in any phase.
         */
        INAPPLICABLE,
        /**
         * An argument names something unresolved, so javac reports that name rather than the call.
         */
        ERRONEOUS_ARGUMENTS
    }

    /**
     * The outcome of an overload resolution.
     *
     * @param outcome how the resolution ended
     * @param methods the chosen method, the ambiguous methods or every inapplicable candidate, depending on the outcome
     */
    public record Resolution(Outcome outcome, List<MethodDescriptor> methods) {
    }

    private enum Phase {
        STRICT,
        LOOSE,
        VARIABLE_ARITY
    }

    private enum Fit {
        YES,
        NO,
        MAYBE;

        private static Fit of(boolean value) {
            return value ? YES : NO;
        }

        private Fit and(Fit other) {
            if (this == NO || other == NO)
                return NO;

            return this == MAYBE || other == MAYBE ? MAYBE : YES;
        }
    }

    private record Argument(Type type, boolean nullLiteral) {
    }
}
