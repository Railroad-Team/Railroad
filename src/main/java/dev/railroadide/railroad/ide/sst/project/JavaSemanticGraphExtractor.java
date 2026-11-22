package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.sst.document.api.Location;
import dev.railroadide.railroad.ide.sst.document.api.TextDocumentSnapshot;
import dev.railroadide.railroad.ide.sst.graph.api.*;
import dev.railroadide.railroad.ide.sst.impl.java.JavaDeclarationAnalysis;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;
import dev.railroadide.railroad.ide.sst.impl.java.JavaUnicodeEscapes;
import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolIds;
import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolKind;
import dev.railroadide.railroad.ide.sst.java.api.JavaTypes;
import dev.railroadide.railroad.ide.sst.semantic.api.*;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Copies resolved Java facts into immutable graph records without retaining syntax nodes or semantic models. */
public final class JavaSemanticGraphExtractor {
    private static final Set<String> MODIFIERS = Set.of("public", "protected", "private", "static", "final",
        "abstract", "synchronized", "native", "strictfp", "transient", "volatile", "default", "sealed", "non-sealed");
    private static final Pattern DOC_LINK = Pattern.compile(
        "(?:\\{@(?:link|linkplain|value)\\s+|@(?:see|throws|exception)\\s+)([\\w.$]*(?:#[\\w$]+(?:\\([^)]*\\))?)?)");
    private final SemanticModel model;
    private final TextDocumentSnapshot snapshot;
    private final Runnable check;
    private final JavaSymbolIndex index;
    private final JavaSourceSymbolIds.Resolver symbolIds;
    private final boolean projectDeclarationsOnly;
    private final Map<SymbolId, SymbolDescriptor> declarations = new LinkedHashMap<>();
    private final Map<ReferenceKey, ReferenceOccurrence> references = new LinkedHashMap<>();
    private final Set<SymbolEdge> edges = new LinkedHashSet<>();

    private JavaSemanticGraphExtractor(
        SemanticModel model,
        JavaSymbolIndex index,
        Runnable check,
        boolean projectDeclarationsOnly
    ) {
        this.model = Objects.requireNonNull(model);
        this.snapshot = model.syntaxTree().documentSnapshot();
        this.check = Objects.requireNonNull(check);
        this.index = index;
        this.symbolIds = new JavaSourceSymbolIds.Resolver(model.syntaxTree().root(), index);
        this.projectDeclarationsOnly = projectDeclarationsOnly;
    }

    /**
     * Extracts precisely the facts present in a model; unresolved references are omitted, never guessed.
     *
     * @param model semantic facts for the captured source revision
     * @param check callback invoked to check cancellation or interruption; may throw to abort
     * @return immutable document shard containing the available declarations, references and relations
     */
    public static SemanticIndexShard extract(SemanticModel model, Runnable check) {
        return extract(model, null, check);
    }

    /**
     * Extracts facts and resolves documentation/import links against the same captured symbol inputs.
     *
     * @param model semantic facts for the captured source revision
     * @param index captured external symbols used for name resolution, or {@code null} for local resolution
     * @param check callback invoked to check cancellation or interruption; may throw to abort
     * @return immutable document shard containing the available declarations, references and relations
     */
    public static SemanticIndexShard extract(SemanticModel model, JavaSymbolIndex index, Runnable check) {
        return extract(model, index, check, false);
    }

    /**
     * Extracts graph facts from project declaration headers.
     *
     * @param model project declaration facts for the captured revision
     * @param index captured external symbols used for name resolution
     * @param check cooperative cancellation callback
     * @return immutable shard containing project declarations and header references
     */
    public static SemanticIndexShard extractProjectDeclarations(
        SemanticModel model,
        JavaSymbolIndex index,
        Runnable check
    ) {
        return extract(model, index, check, true);
    }

    private static SemanticIndexShard extract(
        SemanticModel model,
        JavaSymbolIndex index,
        Runnable check,
        boolean projectDeclarationsOnly
    ) {
        var extractor = new JavaSemanticGraphExtractor(model, index, check, projectDeclarationsOnly);
        extractor.collectDeclarations(model.syntaxTree().root());
        extractor.collectReferences(model.syntaxTree().root(), null);
        check.run();
        return new SemanticIndexShard(extractor.snapshot.id(), extractor.snapshot.version(), extractor.snapshot.uri(),
            List.copyOf(extractor.declarations.values()), List.copyOf(extractor.references.values()),
            List.copyOf(extractor.edges));
    }

    private void collectDeclarations(SyntaxNode node) {
        check.run();
        if (projectDeclarationsOnly && JavaDeclarationAnalysis.isExecutableBody(node))
            return;
        model.declaredSymbol(node).ifPresent(symbol -> JavaSourceSymbolIds.deriveSymbolId(symbol).ifPresent(id -> {
            SyntaxNode container = container(node);
            SyntaxNode owner = JavaSourceSymbolIds.enclosingDeclaration(
                container.kind().id().equals("JAVA_CLASS_INSTANCE_CREATION_EXPRESSION") ? container : node);
            SymbolId ownerId = ownerId(owner);
            if (ownerId == null && isTypeKind(symbol.kind())) {
                String qualified = symbol.qualifiedName().orElse(symbol.simpleName());
                int separator = qualified.lastIndexOf('.');
                ownerId = JavaSymbolIds.packageId("", separator < 0 ? "" : qualified.substring(0, separator));
            }
            SyntaxNode name = nameNode(node, symbol.simpleName());
            TypeReference declaredType = variableArityType(container);
            if (isTypeKind(symbol.kind())) {
                declaredType = new TypeReference(symbol.simpleName(), List.of(), 0, id);
            }
            if (declaredType != null && node != container) {
                int extraArrays = (int) node.children().stream()
                    .filter(part -> part instanceof SyntaxToken token && token.text().equals("[")).count();
                declaredType = new TypeReference(declaredType.name(), declaredType.arguments(),
                    declaredType.arrayDimensions() + extraArrays,
                    declaredType.target());
            }
            var descriptor = new SymbolDescriptor(id, new SymbolKindId(symbol.kind().id()), symbol.simpleName(),
                symbol.qualifiedName().orElse(null), ownerId, signature(container, symbol.kind()), declaredType,
                metadata(container, symbol.kind()), List.of(new SymbolLocation(snapshot.uri(), snapshot.origin(),
                    location(container), location(name), true)));
            declarations.put(id, descriptor);
            addReference(new ReferenceOccurrence(id, ReferenceKindId.DECLARATION, location(name), ownerId));
        }));
        if (node.kind().id().equals("JAVA_RECORD_DECLARATION")) {
            model.declaredSymbol(node).ifPresent(record -> JavaSourceSymbolIds.deriveSymbolId(record)
                .ifPresent(recordId -> addRecordAccessors(node, recordId,
                    record.qualifiedName().orElse(record.simpleName()))));
        }
        if (node.kind().id().equals("JAVA_TYPE_PARAMETER") && model.declaredSymbol(node).isEmpty()) {
            SyntaxNode owner = JavaSourceSymbolIds.enclosingDeclaration(node);
            if (owner != null) {
                SymbolId ownerId = ownerId(owner);
                if (ownerId == null)
                    return;
                SyntaxNode parameters = node.parent().orElseThrow();
                int index = parameters.children().stream()
                    .filter(part -> part.kind().id().equals("JAVA_TYPE_PARAMETER"))
                    .toList().indexOf(node);
                SymbolId id = JavaSymbolIds.typeParameter(ownerId, index);
                String name = JavaSourceSymbolIds.declarationName(node);
                declarations.put(id,
                    new SymbolDescriptor(id, new SymbolKindId(JavaSymbolKind.TYPE_PARAMETER.id()), name,
                        null, ownerId, null, null, SymbolMetadata.EMPTY, List.of(new SymbolLocation(snapshot.uri(),
                            snapshot.origin(), location(node), location(nameNode(node, name)), true))));
            }
        }
        for (SyntaxNode child : node.children()) {
            collectDeclarations(child);
        }
    }

    /**
     * Declares the implicit accessor of each record component at the component, so calls and method references to an
     * accessor have a declaration to navigate to. The record body is collected afterwards, so an explicitly declared
     * accessor replaces the implicit one. No declaration reference is added: the component's own declaration already
     * occupies its name.
     */
    private void addRecordAccessors(SyntaxNode record, SymbolId recordId, String recordName) {
        SyntaxNode header = JavaSourceSymbolIds.child(record, "JAVA_RECORD_HEADER");
        if (header == null)
            return;

        for (SyntaxNode component : header.children()) {
            check.run();
            if (!component.kind().id().equals("JAVA_RECORD_COMPONENT"))
                continue;

            Symbol declared = model.declaredSymbol(component).orElse(null);
            if (declared == null)
                continue;

            String name = declared.simpleName();
            SyntaxNode identifier = nameNode(component, name);
            SymbolId id = JavaSymbolIds.method(recordId, name, List.of());
            TypeReference type = variableArityType(component);
            declarations.put(id, new SymbolDescriptor(id, new SymbolKindId(JavaSymbolKind.METHOD.id()), name,
                recordName + "#" + name + "()", recordId, new SymbolSignature(List.of(), List.of(), type, List.of()),
                type, new SymbolMetadata(Set.of("public"), "public", false, List.of(), Map.of()),
                List.of(new SymbolLocation(snapshot.uri(), snapshot.origin(), location(component), location(identifier),
                    true))));
        }
    }

    private void addReference(ReferenceOccurrence reference) {
        references
            .putIfAbsent(new ReferenceKey(reference.target(), reference.kind(), reference.location().range().start,
                reference.location().range().end, reference.enclosingSymbol()), reference);
    }

    private record ReferenceKey(SymbolId target, ReferenceKindId kind, int start, int end, SymbolId enclosing) {
    }

    private SymbolId ownerId(SyntaxNode owner) {
        try {
            return owner == null
                ? null
                : model.declaredSymbol(owner).flatMap(JavaSourceSymbolIds::deriveSymbolId)
                    .orElseGet(() -> symbolIds.declarationId(owner));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void collectReferences(SyntaxNode node, SymbolId enclosing) {
        check.run();
        if (projectDeclarationsOnly && JavaDeclarationAnalysis.isExecutableBody(node))
            return;
        Optional<SymbolId> declaration = model.declaredSymbol(node).flatMap(JavaSourceSymbolIds::deriveSymbolId);
        SymbolId current = declaration.orElse(enclosing);
        if (node.kind().id().equals("JAVA_TYPE_REFERENCE")
            && node.parent()
                .map(parent -> Set.of("JAVA_EXTENDS_CLAUSE", "JAVA_IMPLEMENTS_CLAUSE").contains(parent.kind().id()))
                .orElse(false)
            && current != null) {
            addInheritance(node, current);
        }
        if (node.kind().id().equals("JAVA_TYPE_REFERENCE")) {
            addTypeUses(node, current);
        }
        if (node.kind().id().equals("JAVA_SWITCH_CASE_ITEM")) {
            addEnumCaseLabel(node, current);
        }
        if (node.kind().id().equals("JAVA_ANONYMOUS_CLASS_BODY") && declaration.isPresent()) {
            SyntaxNode creation = node.parent().orElseThrow();
            SyntaxNode base = JavaSourceSymbolIds.child(creation, "JAVA_TYPE_REFERENCE");
            if (JavaDeclarationAnalysis.isEnumConstantBody(node)) {
                SymbolId enumType = ownerId(JavaSourceSymbolIds.enclosingDeclaration(node));
                if (enumType != null) {
                    edges.add(new SymbolEdge(declaration.get(), enumType, EdgeKindId.SUBTYPE));
                }
            } else if (base != null) {
                addInheritance(base, declaration.get());
            }
        }
        model.resolvedSymbol(node).ifPresent(symbol -> JavaSourceSymbolIds.deriveSymbolId(symbol).ifPresent(target -> {
            if (model.declaredSymbol(node).isPresent())
                return;
            SyntaxNode name = nameNode(node, referenceName(symbol));
            Set<ReferenceKindId> kinds = referenceKinds(node, symbol);
            for (ReferenceKindId kind : kinds) {
                addReference(new ReferenceOccurrence(target, kind, location(name), current));
                if (kind.equals(ReferenceKindId.CALL)) {
                    SymbolId caller = ownerId(JavaSourceSymbolIds.enclosingDeclaration(node));
                    if (caller != null) {
                        edges.add(new SymbolEdge(caller, target, EdgeKindId.CALL));
                    }
                }
                if (kind.equals(ReferenceKindId.INHERITANCE) && current != null) {
                    edges.add(new SymbolEdge(current, target, EdgeKindId.SUBTYPE, inheritanceArguments(node)));
                }
            }
        }));
        if (node instanceof SyntaxToken token && token.kind().id().contains("JAVADOC_COMMENT")) {
            documentation(token, current);
        }
        if (node.kind().id().equals("JAVA_IMPORT_DECLARATION") && index != null) {
            importReferences(node, current);
        }
        if (node.kind().id().equals("JAVA_ANNOTATION") && index != null) {
            String spelling = JavaSourceSymbolIds.compactText(node).replaceFirst("^@", "");
            int arguments = spelling.indexOf('(');
            String name = arguments < 0 ? spelling : spelling.substring(0, arguments);
            SymbolId target = resolvedTypeName(name, node);
            if (target != null) {
                addReference(new ReferenceOccurrence(target, ReferenceKindId.ANNOTATION,
                    location(nameNode(node, name.substring(name.lastIndexOf('.') + 1))), current));
            }
        }
        for (SyntaxNode child : node.children()) {
            collectReferences(child, current);
        }
    }

    private Set<ReferenceKindId> referenceKinds(SyntaxNode node, Symbol symbol) {
        String kind = node.kind().id();
        if (kind.equals("JAVA_METHOD_INVOCATION_EXPRESSION") || kind.equals("JAVA_CLASS_INSTANCE_CREATION_EXPRESSION")
            || kind.equals("JAVA_EXPLICIT_CONSTRUCTOR_INVOCATION"))
            return Set.of(ReferenceKindId.CALL);
        if (kind.equals("JAVA_METHOD_REFERENCE_EXPRESSION"))
            return Set.of(ReferenceKindId.METHOD_REFERENCE);
        if (ancestor(node, "JAVA_IMPORT_DECLARATION") != null)
            return Set.of(ReferenceKindId.IMPORT);
        if (ancestor(node, "JAVA_ANNOTATION") != null)
            return Set.of(ReferenceKindId.ANNOTATION);
        if (ancestor(node, "JAVA_TYPE_ARGUMENTS") != null)
            return Set.of(ReferenceKindId.TYPE_USE);
        if (ancestor(node, "JAVA_EXTENDS_CLAUSE") != null || ancestor(node, "JAVA_IMPLEMENTS_CLAUSE") != null)
            return Set.of(ReferenceKindId.INHERITANCE);
        if (symbol.kind() == JavaSymbolKind.METHOD || symbol.kind() == JavaSymbolKind.CONSTRUCTOR)
            return Set.of();
        if (Set.of(JavaSymbolKind.CLASS, JavaSymbolKind.INTERFACE, JavaSymbolKind.ENUM, JavaSymbolKind.RECORD,
            JavaSymbolKind.ANNOTATION, JavaSymbolKind.TYPE_PARAMETER).contains(symbol.kind()))
            return Set.of(ReferenceKindId.TYPE_USE);
        SyntaxNode current = node;
        for (SyntaxNode parent = node.parent().orElse(null); parent != null; parent = parent.parent().orElse(null)) {
            if (parent.kind().id().equals("JAVA_ARRAY_ACCESS_EXPRESSION"))
                return Set.of(ReferenceKindId.READ);
            if (parent.kind().id().equals("JAVA_FIELD_ACCESS_EXPRESSION")) {
                SyntaxToken selected = lastIdentifier(parent);
                if (selected == null || nameNode(node, symbol.simpleName()).end() != selected.end())
                    return Set.of(ReferenceKindId.READ);
            }
            if (parent.kind().id().equals("JAVA_ASSIGNMENT_EXPRESSION")) {
                SyntaxNode lhs = parent.children().stream().filter(part -> !(part instanceof SyntaxToken)).findFirst()
                    .orElse(null);
                if (lhs == current) {
                    boolean simple = parent.children().stream()
                        .anyMatch(part -> part instanceof SyntaxToken token && token.text().equals("="));
                    return simple ? Set.of(ReferenceKindId.WRITE) : Set.of(ReferenceKindId.READ, ReferenceKindId.WRITE);
                }
                break;
            }
            if (parent.kind().id().equals("JAVA_UNARY_EXPRESSION")
                || parent.kind().id().equals("JAVA_POSTFIX_EXPRESSION")) {
                if (parent.children().stream().anyMatch(part -> part instanceof SyntaxToken token
                    && Set.of("++", "--").contains(token.text())))
                    return Set.of(ReferenceKindId.READ, ReferenceKindId.WRITE);
            }
            if (parent.kind().id().endsWith("STATEMENT") || parent.kind().id().equals("JAVA_ARGUMENT_LIST"))
                break;
            current = parent;
        }
        return Set.of(ReferenceKindId.READ);
    }

    private void addInheritance(SyntaxNode base, SymbolId owner) {
        TypeReference type = typeReference(base);
        SymbolId target = resolvedTypeId(base).orElseGet(() -> resolvedTypeName(type.name(), base));
        if (target == null)
            return;
        edges.add(new SymbolEdge(owner, target, EdgeKindId.SUBTYPE, inheritanceArguments(base)));
        String simpleName = type.name().substring(Math.max(type.name().lastIndexOf('.'),
            type.name().lastIndexOf('$')) + 1);
        addReference(new ReferenceOccurrence(target, ReferenceKindId.INHERITANCE,
            location(nameNode(base, simpleName)), owner));
    }

    /**
     * Records the type named by a type reference and every qualifying type segment, such as {@code Outer} in
     * {@code Outer.Inner}. Segments are recorded only when they resolve to a type with exactly that simple name;
     * package qualifiers and unresolved names are omitted. Inheritance bases are recorded as inheritance instead.
     */
    private void addTypeUses(SyntaxNode type, SymbolId enclosing) {
        List<SyntaxToken> segments = type.children().stream()
            .filter(part -> part instanceof SyntaxToken token && token.kind().id().endsWith("IDENTIFIER"))
            .map(SyntaxToken.class::cast).toList();
        if (segments.isEmpty())
            return;

        SyntaxNode parent = type.parent().orElse(null);
        boolean inheritance = parent != null
            && (Set.of("JAVA_EXTENDS_CLAUSE", "JAVA_IMPLEMENTS_CLAUSE").contains(parent.kind().id())
                || parent.kind().id().equals("JAVA_CLASS_INSTANCE_CREATION_EXPRESSION")
                    && JavaSourceSymbolIds.child(parent, "JAVA_ANONYMOUS_CLASS_BODY") != null);
        SyntaxToken last = segments.getLast();
        if (!inheritance) {
            namedTypeId(type, JavaUnicodeEscapes.translate(last.text())).ifPresent(target -> addReference(
                new ReferenceOccurrence(target, ReferenceKindId.TYPE_USE, location(last), enclosing)));
        }

        var prefix = new StringBuilder();
        for (SyntaxToken segment : segments.subList(0, segments.size() - 1)) {
            prefix.append(prefix.isEmpty() ? "" : ".").append(JavaUnicodeEscapes.translate(segment.text()));
            SymbolId target = resolvedTypeName(prefix.toString(), type);
            if (target != null) {
                addReference(new ReferenceOccurrence(target, ReferenceKindId.TYPE_USE, location(segment), enclosing));
            }
        }
    }

    /**
     * Records an unqualified enum constant label, such as {@code case RED}, which Java resolves against the enum
     * type of the switch selector rather than the enclosing scope.
     */
    private void addEnumCaseLabel(SyntaxNode item, SymbolId enclosing) {
        List<SyntaxToken> tokens = new ArrayList<>();
        Deque<SyntaxNode> pending = new ArrayDeque<>(List.of(item));
        while (!pending.isEmpty()) {
            SyntaxNode next = pending.removeFirst();
            if (model.resolvedSymbol(next).isPresent())
                return;
            if (next instanceof SyntaxToken token) {
                if (!JavaSymbolIds.isTrivia(token)) {
                    tokens.add(token);
                }
            } else {
                pending.addAll(next.children());
            }
        }
        if (tokens.size() != 1 || !tokens.getFirst().kind().id().endsWith("IDENTIFIER"))
            return;

        SyntaxNode selector = null;
        for (SyntaxNode current = item.parent().orElse(null); current != null; current = current.parent()
            .orElse(null)) {
            if (Set.of("JAVA_SWITCH_STATEMENT", "JAVA_SWITCH_EXPRESSION").contains(current.kind().id())) {
                selector = JavaSourceSymbolIds.child(current, "JAVA_PARENTHESIZED_EXPRESSION");
                break;
            }
        }
        if (selector == null)
            return;
        Type type = model.inferredType(selector).orElse(null);
        if (!(type instanceof JavaTypes.DeclaredType)) {
            type = selector.children().stream().filter(part -> !(part instanceof SyntaxToken)).findFirst()
                .flatMap(model::inferredType).orElse(null);
        }
        if (!(type instanceof JavaTypes.DeclaredType declared))
            return;

        String name = JavaUnicodeEscapes.translate(tokens.getFirst().text());
        declaredTypeId(declared, item).ifPresent(enumType -> {
            SymbolId constant = JavaSymbolIds.field(enumType, name);
            boolean known = declarations.containsKey(constant) || index != null
                && index.lookupMember(declared.displayName().replace('$', '.'), name).stream()
                    .anyMatch(symbol -> symbol.descriptor() != null && symbol.descriptor().id().equals(constant));
            if (known) {
                addReference(new ReferenceOccurrence(constant, ReferenceKindId.READ, location(tokens.getFirst()),
                    enclosing));
            }
        });
    }

    private Optional<SymbolId> namedTypeId(SyntaxNode type, String simpleName) {
        Optional<SymbolId> resolved = model.resolvedSymbol(type)
            .filter(symbol -> isTypeKind(symbol.kind()) && referenceName(symbol).equals(simpleName))
            .flatMap(JavaSourceSymbolIds::deriveSymbolId);
        if (resolved.isPresent())
            return resolved;
        if (!(model.inferredType(type).orElse(null) instanceof JavaTypes.DeclaredType declared))
            return Optional.empty();
        String name = declared.displayName();
        if (!name.substring(Math.max(name.lastIndexOf('.'), name.lastIndexOf('$')) + 1).equals(simpleName))
            return Optional.empty();
        return declaredTypeId(declared, type);
    }

    private Optional<SymbolId> declaredTypeId(JavaTypes.DeclaredType declared, SyntaxNode node) {
        List<SymbolDescriptor> candidates = declarations.values().stream()
            .filter(symbol -> isTypeKind(symbol.kind()) && symbol.qualifiedName() != null
                && symbol.qualifiedName().replace('$', '.').equals(declared.displayName().replace('$', '.')))
            .toList();
        if (candidates.size() == 1)
            return Optional.of(candidates.getFirst().id());
        return Optional.ofNullable(resolvedTypeName(declared.displayName(), node));
    }

    private List<TypeReference> inheritanceArguments(SyntaxNode node) {
        SyntaxNode type = ancestor(node, "JAVA_TYPE_REFERENCE");
        if (type == null)
            return List.of();
        return typeReference(type).arguments().stream().map(argument -> canonicalArgument(argument, node)).toList();
    }

    private Optional<SymbolId> resolvedTypeId(SyntaxNode node) {
        Optional<SymbolId> resolved = model.resolvedSymbol(node).filter(symbol -> isTypeKind(symbol.kind()))
            .flatMap(JavaSourceSymbolIds::deriveSymbolId);
        if (resolved.isPresent())
            return resolved;
        Type inferred = model.inferredType(node).orElse(null);
        if (inferred instanceof JavaTypes.DeclaredType declared) {
            resolved = declaredTypeId(declared, node);
            if (resolved.isPresent())
                return resolved;
        }
        for (SyntaxNode child : node.children()) {
            if (!child.kind().id().equals("JAVA_TYPE_ARGUMENTS")) {
                resolved = resolvedTypeId(child);
                if (resolved.isPresent())
                    return resolved;
            }
        }
        return Optional.empty();
    }

    private TypeReference canonicalArgument(TypeReference type, SyntaxNode context) {
        String name = type.name();
        if (!name.startsWith("?") && JavaSourceSymbolIds.typeVariableBound(name, context) == null) {
            try {
                name = symbolIds.qualify(name, context);
            } catch (IllegalArgumentException exception) {
                name = type.name();
            }
        }
        return new TypeReference(name,
            type.arguments().stream().map(argument -> canonicalArgument(argument, context)).toList(),
            type.arrayDimensions(), resolvedTypeName(type.name(), context));
    }

    private void documentation(SyntaxToken token, SymbolId enclosing) {
        var matcher = DOC_LINK.matcher(token.text());
        while (matcher.find()) {
            check.run();
            String text = matcher.group(1);
            if (text.isEmpty())
                continue;
            String name = text.contains("#") ? text.substring(text.indexOf('#') + 1) : text;
            String arguments = name.contains("(") ? name.substring(name.indexOf('(')) : null;
            if (arguments != null) {
                name = name.substring(0, name.indexOf('('));
            }
            final String soughtName = name;
            final String soughtType = text.contains("#") ? text.substring(0, text.indexOf('#')) : null;
            SyntaxNode containing = JavaSourceSymbolIds.enclosingDeclaration(token);
            SymbolDescriptor enclosingDeclaration = containing == null ? null : declarations.get(ownerId(containing));
            SymbolId containingType = enclosingDeclaration == null
                ? null
                : isTypeKind(enclosingDeclaration.kind())
                    ? enclosingDeclaration.id()
                    : enclosingDeclaration.ownerId();
            List<SymbolDescriptor> available = new ArrayList<>(declarations.values());
            if (index != null) {
                if (soughtType == null) {
                    String qualified = qualifiedName(soughtName, token);
                    index.lookupQualifiedName(qualified).stream()
                        .map(JavaProjectSemanticIndex.SymbolDescriptor::descriptor)
                        .filter(Objects::nonNull).forEach(available::add);
                } else if (!soughtType.isEmpty()) {
                    String qualified = qualifiedName(soughtType, token);
                    index.lookupMember(qualified, soughtName).stream()
                        .map(JavaProjectSemanticIndex.SymbolDescriptor::descriptor)
                        .filter(Objects::nonNull).forEach(available::add);
                }
            }
            List<SymbolDescriptor> candidates = available.stream()
                .filter(symbol -> symbol.simpleName().equals(soughtName)
                    || Objects.equals(symbol.qualifiedName(), soughtName))
                .filter(symbol -> soughtType != null || isTypeKind(symbol.kind())
                    || symbol.kind().id().equals("java:type_parameter"))
                .filter(symbol -> soughtType != null || soughtName.contains(".") || visibleType(symbol, token))
                .filter(symbol -> soughtType == null || soughtType.isEmpty() || symbol.qualifiedName() != null
                    && (symbol.qualifiedName().startsWith(soughtType + "#")
                        || symbol.qualifiedName().contains("." + soughtType + "#")))
                .filter(symbol -> soughtType == null || !soughtType.isEmpty() || containingType == null
                    || containingType.equals(symbol.ownerId()))
                .filter(symbol -> arguments == null || symbol.signature() != null
                    && documentationParameters(symbol.signature(), token)
                        .equals(normalizeDocParameters(arguments, token)))
                .collect(Collectors.toMap(SymbolDescriptor::id, symbol -> symbol, (left, right) -> left))
                .values().stream().toList();
            if (candidates.size() == 1) {
                addReference(new ReferenceOccurrence(candidates.getFirst().id(),
                    ReferenceKindId.DOCUMENTATION,
                    Location.TextLocation.from(snapshot, token.start() + matcher.start(1),
                        token.start() + matcher.end(1)),
                    enclosing));
            }
        }
    }

    private String documentationParameters(SymbolSignature signature, SyntaxNode context) {
        return "(" + String.join(",", signature.parameters().stream().map(parameter -> normalizeDocType(
            parameter.type().displayName() + (parameter.varargs() ? "[]" : ""), context)).toList()) + ")";
    }

    private boolean visibleType(SymbolDescriptor symbol, SyntaxNode context) {
        SymbolDescriptor owner = declarations.get(symbol.ownerId());
        if (owner == null || owner.kind().id().equals("java:package") || owner.kind().id().equals("java:module"))
            return true;
        for (SyntaxNode current = context; current != null; current = current.parent().orElse(null)) {
            if (model.declaredSymbol(current).flatMap(JavaSourceSymbolIds::deriveSymbolId).filter(owner.id()::equals)
                .isPresent())
                return true;
        }
        return false;
    }

    private String normalizeDocParameters(String parameters, SyntaxNode context) {
        String content = parameters.substring(1, parameters.length() - 1).trim();
        return "(" + (content.isEmpty()
            ? ""
            : String.join(",", Arrays.stream(content.split(","))
                .map(type -> normalizeDocType(type.trim(), context)).toList()))
            + ")";
    }

    private String normalizeDocType(String type, SyntaxNode context) {
        try {
            String erased = JavaSymbolIds.normalizeParameterType(type);
            String base = erased.replace("[]", "");
            return symbolIds.qualify(base, context) + erased.substring(base.length());
        } catch (IllegalArgumentException exception) {
            return type;
        }
    }

    private void importReferences(SyntaxNode node, SymbolId enclosing) {
        SyntaxNode target = JavaSourceSymbolIds.child(node, "JAVA_IMPORT_TARGET");
        if (target == null || JavaSourceSymbolIds.compactText(node).contains("*"))
            return;
        String name = JavaSourceSymbolIds.compactText(target);
        List<JavaProjectSemanticIndex.SymbolDescriptor> imported = new ArrayList<>(index.lookupQualifiedName(name));
        if (JavaSourceSymbolIds.compactText(node).startsWith("importstatic")) {
            int dot = name.lastIndexOf('.');
            if (dot >= 0) {
                imported.addAll(index.lookupMember(name.substring(0, dot), name.substring(dot + 1)));
            }
        }
        for (var symbol : imported) {
            if (symbol.descriptor() != null) {
                addReference(new ReferenceOccurrence(symbol.descriptor().id(), ReferenceKindId.IMPORT,
                    location(target), enclosing));
            }
        }
    }

    private SymbolSignature signature(SyntaxNode node, SymbolKind kind) {
        if (kind != JavaSymbolKind.METHOD && kind != JavaSymbolKind.CONSTRUCTOR && !isTypeKind(kind))
            return null;
        List<SymbolSignature.Parameter> parameters = new ArrayList<>();
        SyntaxNode list = JavaSourceSymbolIds.parameterList(node);
        if (list != null) {
            for (SyntaxNode parameter : list.children()) {
                if (!parameter.kind().id().equals("JAVA_PARAMETER")
                    && !parameter.kind().id().equals("JAVA_RECORD_COMPONENT"))
                    continue;
                TypeReference type = declaredType(parameter);
                if (type == null) {
                    type = new TypeReference("?");
                }
                SyntaxToken identifier = lastIdentifier(parameter);
                String name = identifier == null ? "" : JavaUnicodeEscapes.translate(identifier.text());
                boolean varargs = isVarargs(parameter);
                int extraArrays = (int) parameter.children().stream()
                    .filter(part -> part instanceof SyntaxToken token && token.text().equals("[")).count();
                if (extraArrays != 0) {
                    type = new TypeReference(type.name(), type.arguments(), type.arrayDimensions() + extraArrays,
                        type.target());
                }
                parameters.add(new SymbolSignature.Parameter(name, type, varargs));
            }
        }
        List<SymbolSignature.TypeParameter> typeParameters = new ArrayList<>();
        SyntaxNode generics = JavaSourceSymbolIds.child(node, "JAVA_TYPE_PARAMETERS");
        if (generics != null) {
            for (SyntaxNode parameter : generics.children()) {
                if (!parameter.kind().id().equals("JAVA_TYPE_PARAMETER"))
                    continue;
                List<TypeReference> bounds = new ArrayList<>();
                collectTypeReferences(parameter, bounds);
                typeParameters
                    .add(new SymbolSignature.TypeParameter(JavaSourceSymbolIds.declarationName(parameter), bounds));
            }
        }
        List<TypeReference> thrown = new ArrayList<>();
        SyntaxNode throwsClause = JavaSourceSymbolIds.child(node, "JAVA_THROWS_CLAUSE");
        if (throwsClause != null) {
            collectTypeReferences(throwsClause, thrown);
        }
        for (int position = 0; position < parameters.size() - 1; position++) {
            if (parameters.get(position).varargs())
                return null;
        }
        return new SymbolSignature(typeParameters, parameters,
            kind == JavaSymbolKind.CONSTRUCTOR || isTypeKind(kind) ? null : declaredType(node), thrown);
    }

    private static boolean isTypeKind(SymbolKind kind) {
        return Set.of("java:class", "java:interface", "java:record", "java:enum", "java:annotation")
            .contains(kind.id());
    }

    private void collectTypeReferences(SyntaxNode node, List<TypeReference> result) {
        for (SyntaxNode child : node.children()) {
            if (child.kind().id().equals("JAVA_TYPE_REFERENCE")) {
                result.add(typeReference(child));
            } else {
                collectTypeReferences(child, result);
            }
        }
    }

    private static boolean isVarargs(SyntaxNode parameter) {
        return parameter.children().stream()
            .anyMatch(part -> part instanceof SyntaxToken token && token.text().equals("..."));
    }

    private TypeReference variableArityType(SyntaxNode node) {
        TypeReference type = declaredType(node);
        return type != null && isVarargs(node)
            ? new TypeReference(type.name(), type.arguments(), type.arrayDimensions() + 1, type.target())
            : type;
    }

    private TypeReference declaredType(SyntaxNode node) {
        SyntaxNode type = JavaSourceSymbolIds.child(node, "JAVA_TYPE_REFERENCE");
        if (type == null)
            return null;
        TypeReference result = typeReference(type);
        if (node.kind().id().equals("JAVA_METHOD_DECLARATION")) {
            int trailingDimensions = (int) node.children().stream()
                .filter(part -> part.kind().id().equals("JAVA_ARRAY_DIMENSION")).count();
            result = new TypeReference(result.name(), result.arguments(),
                result.arrayDimensions() + trailingDimensions, result.target());
        }
        return result;
    }

    private TypeReference typeReference(SyntaxNode node) {
        String spelling = JavaSemanticAnalyzer.canonicalTypeText(node);
        TypeReference parsed = parseType(spelling == null || spelling.isBlank() ? "?" : spelling);
        String simpleName = parsed.name().substring(parsed.name().lastIndexOf('.') + 1);
        SymbolId target = model.resolvedSymbol(node).flatMap(JavaSourceSymbolIds::deriveSymbolId)
            .or(() -> namedTypeId(node, simpleName)).orElse(null);
        return new TypeReference(parsed.name(), parsed.arguments(), parsed.arrayDimensions(), target);
    }

    /**
     * Parses a Java type spelling into generic arguments, wildcard bounds and array rank.
     *
     * @param text Java type spelling to split into structured components
     * @return structured type spelling without a resolved symbol target
     */
    public static TypeReference parseType(String text) {
        text = text.trim();
        if (text.isEmpty())
            return new TypeReference("?");
        int rank = 0;
        while (text.endsWith("[]")) {
            rank++;
            text = text.substring(0, text.length() - 2).trim();
        }
        if (text.startsWith("? extends ") || text.startsWith("? super ")) {
            int split = text.startsWith("? extends ") ? 9 : 7;
            return new TypeReference(text.substring(0, split).trim(), List.of(parseType(text.substring(split))), rank,
                null);
        }
        int open = text.indexOf('<');
        List<TypeReference> arguments = new ArrayList<>();
        String name = text;
        if (open >= 0 && text.endsWith(">")) {
            name = text.substring(0, open).trim();
            int depth = 0;
            int start = open + 1;
            for (int index = start; index < text.length(); index++) {
                char c = text.charAt(index);
                if (c == '<') {
                    depth++;
                }
                if (c == '>') {
                    depth--;
                }
                if (c == ',' && depth == 0 || index == text.length() - 1) {
                    arguments.add(parseType(text.substring(start, index)));
                    start = index + 1;
                }
            }
        }
        return new TypeReference(name, arguments, rank, null);
    }

    private SymbolMetadata metadata(SyntaxNode node, SymbolKind kind) {
        Set<String> modifiers = new LinkedHashSet<>();
        List<TypeReference> annotations = new ArrayList<>();
        for (SyntaxNode part : node.children()) {
            if (part instanceof SyntaxToken token && MODIFIERS.contains(JavaUnicodeEscapes.translate(token.text()))) {
                modifiers.add(JavaUnicodeEscapes.translate(token.text()));
            }
            if (part.kind().id().equals("JAVA_ANNOTATION")) {
                String text = JavaSourceSymbolIds.compactText(part).replaceFirst("^@", "");
                int arguments = text.indexOf('(');
                String name = arguments < 0 ? text : text.substring(0, arguments);
                annotations.add(new TypeReference(name, List.of(), 0, resolvedTypeName(name, part)));
            }
        }
        SyntaxNode owner = JavaSourceSymbolIds.enclosingDeclaration(node);
        if (node.kind().id().equals("JAVA_ENUM_CONSTANT")) {
            modifiers.addAll(Set.of("public", "static", "final"));
        }
        if (kind == JavaSymbolKind.RECORD) {
            modifiers.add("final");
        }
        if (kind == JavaSymbolKind.ENUM) {
            SyntaxNode body = JavaSourceSymbolIds.child(node, "JAVA_ENUM_BODY");
            boolean specializedConstants = body != null && body.children().stream()
                .filter(part -> part.kind().id().equals("JAVA_ENUM_CONSTANT"))
                .anyMatch(part -> JavaSourceSymbolIds.child(part, "JAVA_ANONYMOUS_CLASS_BODY") != null);
            modifiers.add(specializedConstants ? "sealed" : "final");
        }
        if (kind == JavaSymbolKind.CONSTRUCTOR && owner != null
            && owner.kind().id().equals("JAVA_ENUM_DECLARATION")) {
            modifiers.add("private");
        }
        if (kind == JavaSymbolKind.INTERFACE || kind == JavaSymbolKind.ANNOTATION) {
            modifiers.add("abstract");
        }
        if (owner != null && Set.of(JavaSymbolKind.INTERFACE, JavaSymbolKind.ANNOTATION, JavaSymbolKind.ENUM,
            JavaSymbolKind.RECORD).contains(kind)) {
            modifiers.add("static");
        }
        if (owner != null
            && Set.of("JAVA_INTERFACE_DECLARATION", "JAVA_ANNOTATION_TYPE_DECLARATION").contains(owner.kind().id())) {
            if (!modifiers.contains("private")) {
                modifiers.add("public");
            }
            if (kind == JavaSymbolKind.FIELD) {
                modifiers.addAll(Set.of("static", "final"));
            }
            if (Set.of(JavaSymbolKind.CLASS, JavaSymbolKind.INTERFACE, JavaSymbolKind.ANNOTATION,
                JavaSymbolKind.ENUM, JavaSymbolKind.RECORD).contains(kind)) {
                modifiers.add("static");
            }
            if (kind == JavaSymbolKind.METHOD && !modifiers.contains("default") && !modifiers.contains("static")
                && !modifiers.contains("private")) {
                modifiers.add("abstract");
            }
        }
        String visibility = modifiers.contains("public")
            ? "public"
            : modifiers.contains("protected")
                ? "protected"
                : modifiers.contains("private") ? "private" : "package";
        boolean deprecated = annotations.stream()
            .anyMatch(annotation -> Set.of("Deprecated", "java.lang.Deprecated").contains(annotation.name()))
            || node.children().stream().anyMatch(part -> part instanceof SyntaxToken token
                && token.kind().id().contains("JAVADOC_COMMENT") && token.text().contains("@deprecated"));
        return new SymbolMetadata(modifiers, visibility, deprecated, annotations, Map.of());
    }

    private Location.TextLocation location(SyntaxNode node) {
        return Location.TextLocation.from(snapshot, node.start(), node.end());
    }

    private SymbolId resolvedTypeName(String name, SyntaxNode context) {
        if (index == null)
            return null;
        String qualified = qualifiedName(name, context);
        if (qualified == null)
            return null;
        String candidate = qualified;
        for (int dot = candidate.length(); dot > 0; dot = candidate.lastIndexOf('.', dot - 1)) {
            if (dot < candidate.length()) {
                candidate = candidate.substring(0, dot) + "$" + candidate.substring(dot + 1);
            }
            List<JavaProjectSemanticIndex.SymbolDescriptor> candidates = index.lookupQualifiedName(candidate);
            if (!candidates.isEmpty())
                return candidates.size() == 1 && candidates.getFirst().descriptor() != null
                    ? candidates.getFirst().descriptor().id()
                    : null;
        }
        return null;
    }

    private String qualifiedName(String name, SyntaxNode context) {
        try {
            return symbolIds.qualify(name, context).replace('$', '.');
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static SyntaxNode container(SyntaxNode node) {
        return node.kind().id().equals("JAVA_VARIABLE_DECLARATOR")
            || node.kind().id().equals("JAVA_ANONYMOUS_CLASS_BODY") && !JavaDeclarationAnalysis.isEnumConstantBody(node)
                ? node.parent().orElse(node)
                : node;
    }

    /**
     * Returns the name a reference to a symbol is spelled with. Binary constructors are named {@code <init>} and binary
     * member types by their binary name, such as {@code Outer$Inner}, but both are referenced by a simple name.
     */
    private static String referenceName(Symbol symbol) {
        String name = symbol.simpleName();
        if (symbol.kind() == JavaSymbolKind.CONSTRUCTOR && name.equals("<init>")) {
            name = symbol.qualifiedName().orElse("");
            int member = name.indexOf('#');
            if (member >= 0) {
                name = name.substring(0, member);
            }
            name = name.substring(name.lastIndexOf('.') + 1);
        }
        return isTypeKind(symbol.kind()) || symbol.kind() == JavaSymbolKind.CONSTRUCTOR
            ? name.substring(name.lastIndexOf('$') + 1)
            : name;
    }

    private static SyntaxNode nameNode(SyntaxNode node, String name) {
        if (node.kind().id().equals("JAVA_METHOD_INVOCATION_EXPRESSION")) {
            SyntaxNode selector = node.children().stream()
                .filter(part -> part.kind().id().equals("JAVA_NAME_EXPRESSION"))
                .reduce((left, right) -> right).orElse(null);
            if (selector != null)
                return nameNode(selector, name);
            SyntaxNode keyword = node.children().stream()
                .filter(part -> part.kind().id().equals("JAVA_THIS_EXPRESSION")
                    || part.kind().id().equals("JAVA_SUPER_EXPRESSION"))
                .findFirst().orElse(null);
            // Explicit constructor invocations have no name token; the keyword is the reference.
            if (keyword != null && node.children().getFirst() == keyword)
                return keyword;
        }
        if (node.kind().id().equals("JAVA_FIELD_ACCESS_EXPRESSION")) {
            SyntaxToken selected = lastIdentifier(node);
            if (selected != null && JavaUnicodeEscapes.translate(selected.text()).equals(name))
                return selected;
        }
        for (SyntaxNode child : node.children()) {
            if (child instanceof SyntaxToken token && JavaUnicodeEscapes.translate(token.text()).equals(name))
                return child;
        }
        if (node instanceof SyntaxToken token && JavaUnicodeEscapes.translate(token.text()).equals(name))
            return node;
        for (SyntaxNode child : node.children()) {
            if (child.kind().id().endsWith("BODY") || child.kind().id().equals("JAVA_BLOCK"))
                continue;
            SyntaxNode found = nameNode(child, name);
            if (found instanceof SyntaxToken token && JavaUnicodeEscapes.translate(token.text()).equals(name))
                return found;
        }
        return node;
    }

    private static SyntaxToken lastIdentifier(SyntaxNode node) {
        SyntaxToken result = null;
        for (SyntaxNode child : node.children()) {
            if (child instanceof SyntaxToken token && token.kind().id().endsWith("IDENTIFIER")) {
                result = token;
            } else if (!(child instanceof SyntaxToken)) {
                SyntaxToken nested = lastIdentifier(child);
                if (nested != null) {
                    result = nested;
                }
            }
        }
        return result;
    }

    private static SyntaxNode lastNonToken(SyntaxNode node) {
        return node.children().stream().filter(part -> !(part instanceof SyntaxToken)).reduce((left, right) -> right)
            .orElse(node);
    }

    private static SyntaxNode ancestor(SyntaxNode node, String kind) {
        for (SyntaxNode current = node; current != null; current = current.parent().orElse(null)) {
            if (current.kind().id().equals(kind))
                return current;
        }
        return null;
    }
}
