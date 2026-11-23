package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.graph.api.*;
import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolIds;
import dev.railroadide.railroad.ide.sst.semantic.api.*;
import java.util.*;

/** Adds certain Java override/implementation relations from captured declarations and parameterized hierarchy facts. */
public final class JavaOverrideGraphExtractor {
    private static final TypeReference OBJECT = new TypeReference("java.lang.Object");
    private static final Set<String> CLASS_KINDS = Set.of("java:class", "java:enum", "java:record");
    private final Map<SymbolId, SymbolDescriptor> declarations = new HashMap<>();
    private final Map<SymbolId, List<SymbolDescriptor>> methods = new HashMap<>();
    private final Map<SymbolId, List<SymbolEdge>> parents = new HashMap<>();
    private final Map<String, List<SymbolDescriptor>> typesByName = new HashMap<>();
    private final Map<SymbolId, List<Binding>> bindings = new HashMap<>();
    private final Map<SymbolId, Map<String, List<SymbolDescriptor>>> methodNames = new HashMap<>();
    private final Runnable check;
    private final JavaSymbolIndex symbols;
    private final JavaBinarySymbolExtractor.TypeReferences binaryTypes = new JavaBinarySymbolExtractor.TypeReferences();

    private JavaOverrideGraphExtractor(Collection<SemanticIndexShard> shards, JavaSymbolIndex symbols, Runnable check) {
        this.check = check;
        this.symbols = symbols;
        for (var shard : shards) {
            check.run();
            for (var symbol : shard.declarations()) {
                check.run();
                declarations.putIfAbsent(symbol.id(), symbol);
                if (Set.of("java:class", "java:interface", "java:record", "java:annotation", "java:enum")
                    .contains(symbol.kind().id())) {
                    typesByName.computeIfAbsent(symbol.simpleName(), _ -> new ArrayList<>()).add(symbol);
                    if (symbol.qualifiedName() != null && !symbol.qualifiedName().equals(symbol.simpleName())) {
                        typesByName.computeIfAbsent(symbol.qualifiedName(), _ -> new ArrayList<>()).add(symbol);
                    }
                }
                if (eligible(symbol)) {
                    methods.computeIfAbsent(symbol.ownerId(), _ -> new ArrayList<>()).add(symbol);
                }
            }
            for (var edge : shard.edges()) {
                if (edge.kind().equals(EdgeKindId.SUBTYPE)) {
                    parents.computeIfAbsent(edge.source(), _ -> new ArrayList<>()).add(edge);
                }
            }
        }
    }

    /**
     * Derives confirmed override relations from captured declarations and subtype edges.
     *
     * @param shards captured document shards with unique document identities
     * @param check callback invoked to check cancellation or interruption; may throw to abort
     * @return shards enriched with confirmed override relations
     */
    public static List<SemanticIndexShard> extract(Collection<SemanticIndexShard> shards, Runnable check) {
        return extract(shards, null, check);
    }

    /**
     * Derives overrides using class stubs to check covariant returns whose types have no declaration in the graph.
     *
     * @param shards declarations and subtype edges in the graph
     * @param symbols captured lookup for additional binary supertypes
     * @param check cancellation check
     * @return shards enriched with override relations
     */
    public static List<SemanticIndexShard> extract(
        Collection<SemanticIndexShard> shards,
        JavaSymbolIndex symbols,
        Runnable check
    ) {
        return new JavaOverrideGraphExtractor(shards, symbols, check).extract(shards);
    }

    /**
     * Finds methods that implement an interface method only because a subclass inherits both, such as
     * {@code Base.draw()} implementing {@code Shape.draw()} in {@code class Circle extends Base implements Shape}.
     * Such pairs have no override relation, since neither declaring type is a subtype of the other.
     *
     * @param shards captured document shards with unique document identities
     * @param documents documents whose declared types are examined as the inheriting subclass
     * @param symbols captured lookup for additional binary supertypes
     * @param check callback invoked to check cancellation or interruption; may throw to abort
     * @return pairs found, in declaration order
     */
    public static List<InheritedImplementation> inheritedImplementations(
        Collection<SemanticIndexShard> shards,
        Set<DocumentId> documents,
        JavaSymbolIndex symbols,
        Runnable check
    ) {
        var extractor = new JavaOverrideGraphExtractor(shards, symbols, check);
        Set<InheritedImplementation> found = new LinkedHashSet<>();
        for (var shard : shards) {
            if (!documents.contains(shard.documentId()))
                continue;
            for (var type : shard.declarations()) {
                check.run();
                if (CLASS_KINDS.contains(type.kind().id())) {
                    extractor.inheritedImplementations(type.id(), found);
                }
            }
        }
        return List.copyOf(found);
    }

    private void inheritedImplementations(SymbolId type, Set<InheritedImplementation> found) {
        List<Binding> ancestors = bindings.computeIfAbsent(type, this::ancestorBindings);
        for (var inherited : ancestors) {
            SymbolDescriptor owner = declarations.get(inherited.type());
            if (owner == null || !CLASS_KINDS.contains(owner.kind().id()))
                continue;
            for (var implemented : ancestors) {
                SymbolDescriptor contract = declarations.get(implemented.type());
                if (contract == null || !contract.kind().id().equals("java:interface")
                    || bindings.computeIfAbsent(inherited.type(), this::ancestorBindings).stream()
                        .anyMatch(binding -> binding.type().equals(implemented.type())))
                    continue;
                Map<String, List<SymbolDescriptor>> candidates = methodsByName(inherited.type());
                boolean samePackage = packageName(owner).equals(packageName(declarations.get(type)));
                for (var base : methods.getOrDefault(implemented.type(), List.of())) {
                    for (var method : candidates.getOrDefault(base.simpleName(), List.of())) {
                        check.run();
                        if ((samePackage || !method.metadata().visibility().equals("package"))
                            && matches(method, inherited.arguments(), base, implemented.arguments())) {
                            found.add(new InheritedImplementation(method.id(), base.id(), type));
                        }
                    }
                }
            }
        }
    }

    private Map<String, List<SymbolDescriptor>> methodsByName(SymbolId type) {
        return methodNames.computeIfAbsent(type, owner -> {
            Map<String, List<SymbolDescriptor>> byName = new HashMap<>();
            for (var method : methods.getOrDefault(owner, List.of())) {
                byName.computeIfAbsent(method.simpleName(), _ -> new ArrayList<>()).add(method);
            }
            return byName;
        });
    }

    private List<SemanticIndexShard> extract(Collection<SemanticIndexShard> shards) {
        List<SemanticIndexShard> result = new ArrayList<>();
        for (var shard : shards) {
            check.run();
            Set<SymbolEdge> edges = new LinkedHashSet<>(shard.edges());
            for (var method : shard.declarations()) {
                if (!eligible(method))
                    continue;
                Map<String, TypeReference> own = variables(declarations.get(method.ownerId()), Map.of(),
                    method.ownerId().value());
                for (var binding : bindings.computeIfAbsent(method.ownerId(), this::ancestorBindings)) {
                    for (var base : methods.getOrDefault(binding.type(), List.of())) {
                        check.run();
                        if (!method.simpleName().equals(base.simpleName())
                            || base.metadata().modifiers().contains("final"))
                            continue;
                        if (base.metadata().visibility().equals("package")
                            && !packageName(declarations.get(method.ownerId()))
                                .equals(packageName(declarations.get(binding.type()))))
                            continue;
                        if (matches(method, own, base, binding.arguments())) {
                            edges.add(new SymbolEdge(method.id(), base.id(), EdgeKindId.OVERRIDE));
                        }
                    }
                }
            }
            result.add(edges.size() == shard.edges().size()
                ? shard
                : new SemanticIndexShard(shard.documentId(),
                    shard.documentVersion(), shard.uri(), shard.declarations(), shard.references(),
                    List.copyOf(edges)));
        }
        return List.copyOf(result);
    }

    private boolean matches(
        SymbolDescriptor method,
        Map<String, TypeReference> own,
        SymbolDescriptor base,
        Map<String, TypeReference> inherited
    ) {
        if (method.signature().parameters().size() != base.signature().parameters().size())
            return false;
        var left = variables(method, own, "method");
        var right = variables(base, inherited, "method");
        boolean exact = method.signature().typeParameters().size() == base.signature().typeParameters().size();
        boolean erased = method.signature().typeParameters().isEmpty();
        if (exact) {
            for (int index = 0; index < method.signature().typeParameters().size(); index++) {
                var ownBounds = method.signature().typeParameters().get(index).bounds();
                var inheritedBounds = base.signature().typeParameters().get(index).bounds();
                exact &= ownBounds.stream().map(bound -> typeKey(substitute(bound, left), method, false)).toList()
                    .equals(
                        inheritedBounds.stream().map(bound -> typeKey(substitute(bound, right), base, false)).toList());
            }
        }
        for (int index = 0; index < method.signature().parameters().size(); index++) {
            var parameter = method.signature().parameters().get(index);
            var ancestorParameter = base.signature().parameters().get(index);
            String actual = parameterKey(parameter, left, method, false);
            exact &= actual.equals(parameterKey(ancestorParameter, right, base, false));
            erased &= actual.equals(parameterKey(ancestorParameter, right, base, true));
        }
        if (!exact && !erased)
            return false;
        var returned = method.signature().returnType();
        var inheritedReturn = base.signature().returnType();
        if (returned == null || inheritedReturn == null)
            return false;
        returned = substitute(returned, left);
        inheritedReturn = substitute(inheritedReturn, right);
        if (typeKey(returned, method, false).equals(typeKey(inheritedReturn, base, false)))
            return true;
        if (erased && typeKey(returned, method, true).equals(typeKey(inheritedReturn, base, true)))
            return true;
        SymbolId returnedId = resolvedType(returned, method);
        SymbolId inheritedId = resolvedType(inheritedReturn, base);
        return returnedId != null && inheritedId != null
            && returned.arrayDimensions() == inheritedReturn.arrayDimensions()
            && ancestorBindings(returnedId).stream().anyMatch(binding -> binding.type().equals(inheritedId));
    }

    private String parameterKey(
        SymbolSignature.Parameter parameter,
        Map<String, TypeReference> environment,
        SymbolDescriptor context,
        boolean erased
    ) {
        return typeKey(substitute(parameter.type(), environment), context, erased) + (parameter.varargs() ? "[]" : "");
    }

    private List<Binding> ancestorBindings(SymbolId type) {
        Map<String, TypeReference> own = variables(declarations.get(type), Map.of(), type.value());
        List<Binding> result = new ArrayList<>();
        var queue = new ArrayDeque<Binding>();
        queue.add(new Binding(type, own));
        Set<SymbolId> seen = new HashSet<>();
        seen.add(type);
        while (!queue.isEmpty()) {
            check.run();
            var current = queue.removeFirst();
            for (var edge : parents.computeIfAbsent(current.type(), this::binaryParents)) {
                if (!seen.add(edge.target()))
                    continue;
                var target = declarations.get(edge.target());
                List<SymbolSignature.TypeParameter> parameters = target == null || target.signature() == null
                    ? List.of()
                    : target.signature().typeParameters();
                Map<String, TypeReference> arguments = new HashMap<>();
                for (int index = 0; index < parameters.size(); index++) {
                    var parameter = parameters.get(index);
                    TypeReference value = index < edge.typeArguments().size()
                        ? substitute(edge.typeArguments().get(index), current.arguments())
                        : parameter.bounds().isEmpty()
                            ? OBJECT
                            : erase(substitute(parameter.bounds().getFirst(), arguments));
                    arguments.put(parameter.name(), value);
                }
                var binding = new Binding(edge.target(), Map.copyOf(arguments));
                result.add(binding);
                queue.addLast(binding);
            }
        }
        return result;
    }

    private List<SymbolEdge> binaryParents(SymbolId type) {
        if (symbols == null || declarations.containsKey(type))
            return List.of();
        String name = JavaSymbolIds.owningTypeName(type);
        if (name == null || !type.equals(JavaSymbolIds.type(name)))
            return List.of();
        var stub = symbols.lookupClassStub(name);
        return stub == null ? List.of() : JavaBinarySymbolExtractor.supertypeEdges(stub, type, binaryTypes);
    }

    private static Map<String, TypeReference> variables(
        SymbolDescriptor declaration,
        Map<String, TypeReference> inherited,
        String owner
    ) {
        var result = new HashMap<>(inherited);
        if (declaration == null || declaration.signature() == null)
            return result;
        var parameters = declaration.signature().typeParameters();
        for (int index = 0; index < parameters.size(); index++) {
            result.put(parameters.get(index).name(),
                new TypeReference("~" + owner + "/" + index, List.of(OBJECT), 0, null));
        }
        for (int index = 0; index < parameters.size(); index++) {
            var parameter = parameters.get(index);
            var bound = parameter.bounds().isEmpty() ? OBJECT : substitute(parameter.bounds().getFirst(), result);
            result.put(parameter.name(), new TypeReference("~" + owner + "/" + index, List.of(bound), 0, null));
        }
        return result;
    }

    private static TypeReference substitute(TypeReference type, Map<String, TypeReference> environment) {
        var variable = environment.get(type.name());
        if (variable != null)
            return new TypeReference(variable.name(), variable.arguments(),
                variable.arrayDimensions() + type.arrayDimensions(), variable.target());
        return new TypeReference(type.name(),
            type.arguments().stream().map(argument -> substitute(argument, environment)).toList(),
            type.arrayDimensions(), type.target());
    }

    private static TypeReference erase(TypeReference type) {
        if (type.name().startsWith("~")) {
            var bound = type.arguments().isEmpty() ? OBJECT : erase(type.arguments().getFirst());
            return new TypeReference(bound.name(), List.of(), bound.arrayDimensions() + type.arrayDimensions(),
                bound.target());
        }
        return new TypeReference(type.name(), List.of(), type.arrayDimensions(), type.target());
    }

    private String typeKey(TypeReference type, SymbolDescriptor context, boolean erased) {
        if (erased) {
            type = erase(type);
        }
        String name = type.name();
        SymbolId resolved = name.startsWith("~") ? null : resolvedType(type, context);
        if (resolved != null) {
            name = resolved.value();
        }
        if (!erased && !type.name().startsWith("~") && !type.arguments().isEmpty()) {
            name += "<" + String.join(",",
                type.arguments().stream().map(argument -> typeKey(argument, context, false)).toList()) + ">";
        }
        return name + "[]".repeat(type.arrayDimensions());
    }

    private SymbolId resolvedType(TypeReference type, SymbolDescriptor context) {
        if (type.target() != null)
            return type.target();
        String name = type.name();
        if (name.startsWith("~"))
            return null;
        String localPackage = packageName(declarations.get(context.ownerId()));
        List<SymbolDescriptor> candidates = typesByName.getOrDefault(name, List.of());
        var exact = candidates.stream().filter(symbol -> name.equals(symbol.qualifiedName())
            || (localPackage + "." + name).equals(symbol.qualifiedName())).findFirst();
        return exact.map(SymbolDescriptor::id)
            .orElseGet(() -> candidates.size() == 1 ? candidates.getFirst().id() : null);
    }

    private static boolean eligible(SymbolDescriptor symbol) {
        return symbol.kind().id().equals("java:method") && symbol.ownerId() != null && symbol.signature() != null
            && !symbol.metadata().modifiers().contains("static") && !symbol.metadata().visibility().equals("private");
    }

    private String packageName(SymbolDescriptor type) {
        Set<SymbolId> visited = new HashSet<>();
        while (type != null && visited.add(type.id())) {
            if (type.kind().id().equals("java:package"))
                return type.qualifiedName() == null ? "" : type.qualifiedName();
            var owner = declarations.get(type.ownerId());
            if (owner == null)
                break;
            type = owner;
        }
        if (type == null || type.qualifiedName() == null)
            return "";
        String name = type.qualifiedName();
        int lastDot = name.lastIndexOf('.');
        return lastDot < 0 ? "" : name.substring(0, lastDot);
    }

    private record Binding(SymbolId type, Map<String, TypeReference> arguments) {
    }

    /**
     * A method that implements an interface method in a subclass inheriting both.
     *
     * @param implementation inherited method of a superclass
     * @param method implemented interface method
     * @param type subclass inheriting both
     */
    public record InheritedImplementation(SymbolId implementation, SymbolId method, SymbolId type) {
        /**
         * Validates that all identities are present.
         *
         * @param implementation inherited method of a superclass
         * @param method implemented interface method
         * @param type subclass inheriting both
         */
        public InheritedImplementation {
            Objects.requireNonNull(implementation, "implementation");
            Objects.requireNonNull(method, "method");
            Objects.requireNonNull(type, "type");
        }
    }
}
