package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.language.impl.index.JavaWorkspaceAnalysis;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.edit.api.ChangeAnnotation;
import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceChange;
import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceEdit;
import dev.railroadide.railroad.ide.sst.graph.api.*;
import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolIds;
import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolKind;
import dev.railroadide.railroad.ide.sst.project.JavaOverrideGraphExtractor;
import dev.railroadide.railroad.ide.sst.project.JavaOverrideGraphExtractor.InheritedImplementation;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticIndex;
import dev.railroadide.railroad.ide.sst.provider.api.RenameConflict;
import dev.railroadide.railroad.ide.sst.provider.api.RenamePreparation;
import dev.railroadide.railroad.ide.sst.provider.api.RenameProposal;
import dev.railroadide.railroad.ide.sst.request.api.FeatureRequestContext;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolDescriptor;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolId;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolLocation;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken;
import dev.railroadide.railroad.plugin.spi.editor.TextReplacement;
import dev.railroadide.railroad.plugin.spi.editor.TextSelection;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Computes Java renames from the resolved workspace graph of one captured workspace.
 * <p>
 * Occurrences are taken only from resolved references; nothing is renamed by matching text. Every proposed rename
 * is verified by analysing the renamed workspace again: each reference must resolve to the same declaration as
 * before, no other reference may start resolving to a renamed declaration, override and subtype relations must be
 * unchanged, and no two declarations may end up with the same identity. Any difference is reported as a conflict.
 * <p>
 * The analysis only approximates Java's overload resolution, so an unchanged analysed resolution does not prove an
 * unchanged compiled one. Method renames that add an overload existing calls could choose, that change which
 * enclosing type an unqualified call is resolved in, or that produce an unqualified call to {@code yield} are
 * therefore rejected rather than verified.
 */
public final class JavaRenameEngine {
    private static final Set<String> KEYWORDS = Set.of("abstract", "assert", "boolean", "break", "byte", "case",
        "catch", "char", "class", "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
        "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long",
        "native", "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
        "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile",
        "while", "true", "false", "null", "_", "non-sealed");
    private static final Set<String> RESTRICTED_TYPE_NAMES = Set.of("var", "yield", "record", "sealed", "permits");
    private static final Set<String> TYPE_KINDS = Set.of("java:class", "java:interface", "java:enum",
        "java:record", "java:annotation");
    private static final String FIELD = "java:field";
    private static final String METHOD = "java:method";
    private static final String CONSTRUCTOR = "java:constructor";
    private static final String PARAMETER = "java:parameter";
    private static final String LOCAL = "java:local_variable";
    private static final Set<String> NAMED_COMPONENTS = Set.of("field", "method", "name");
    private static final Set<String> SCOPE_KINDS = Set.of("JAVA_BLOCK", "JAVA_FOR_STATEMENT",
        "JAVA_BASIC_FOR_STATEMENT", "JAVA_ENHANCED_FOR_STATEMENT", "JAVA_TRY_STATEMENT", "JAVA_CATCH_CLAUSE",
        "JAVA_LAMBDA_EXPRESSION", "JAVA_METHOD_DECLARATION", "JAVA_CONSTRUCTOR_DECLARATION", "JAVA_SWITCH_RULE",
        "JAVA_IF_STATEMENT", "JAVA_WHILE_STATEMENT");
    private static final Set<String> TYPE_BODY_KINDS = Set.of("JAVA_CLASS_BODY", "JAVA_INTERFACE_BODY",
        "JAVA_ENUM_BODY", "JAVA_RECORD_BODY", "JAVA_ANNOTATION_TYPE_BODY", "JAVA_ANONYMOUS_CLASS_BODY");
    private static final Map<String, String> OBJECT_METHODS = Map.of("toString", "", "hashCode", "",
        "equals", "java.lang.Object", "clone", "", "finalize", "");
    private static final Set<String> PRIMITIVES = Set.of("boolean", "byte", "short", "char", "int", "long",
        "float", "double");
    private static final Set<String> BOXING_TYPES = Set.of("java.lang.Object", "java.lang.Number",
        "java.io.Serializable", "java.lang.Comparable", "java.lang.constant.Constable",
        "java.lang.constant.ConstantDesc", "java.lang.Boolean", "java.lang.Byte", "java.lang.Short",
        "java.lang.Character", "java.lang.Integer", "java.lang.Long", "java.lang.Float", "java.lang.Double");
    private static final SymbolId OBJECT = JavaSymbolIds.type("java.lang.Object");
    private static final int REPORTED_UNRESOLVED_LIMIT = 20;
    private static final String OCCURRENCES = "rename.occurrences";
    private static final String FILE = "rename.file";

    private final FeatureRequestContext context;
    private final WorkspaceSnapshot workspace;
    private final Function<FeatureRequestContext, JavaWorkspaceAnalysis> capture;
    private final WorkspaceSymbolGraph graph;
    private final Map<DocumentId, TextDocumentSnapshot> sources = new LinkedHashMap<>();
    private @Nullable JavaWorkspaceAnalysis analysis;
    private @Nullable List<InheritedImplementation> implementations;
    private @Nullable Map<SymbolId, List<SymbolDescriptor>> sourceMembers;
    private final Map<MemberQuery, List<Member>> members = new HashMap<>();
    private final Map<DocumentId, SyntaxNode> trees = new HashMap<>();

    /**
     * Pins the resolved graph of the request's workspace.
     *
     * @param context captured requesting document and workspace
     * @param capture analysis capture for a request context, used again to verify the renamed workspace
     */
    public JavaRenameEngine(
        FeatureRequestContext context,
        Function<FeatureRequestContext, JavaWorkspaceAnalysis> capture
    ) {
        this.context = Objects.requireNonNull(context, "context");
        this.workspace = context.workspaceSnapshot();
        this.capture = Objects.requireNonNull(capture, "capture");
        this.graph = capture.apply(context).resolvedGraph(context::checkActive);
        for (SemanticIndexShard shard : graph.shards().values()) {
            if (workspace.document(shard.documentId()).orElse(null) instanceof TextDocumentSnapshot snapshot
                && snapshot.version().equals(shard.documentVersion()) && snapshot.uri().equals(shard.uri())
                && "java".equals(snapshot.languageId())) {
                sources.put(snapshot.id(), snapshot);
            }
        }
    }

    /**
     * Checks the symbol at an offset of the requesting document.
     *
     * @param offset UTF-16 offset of, or just after, the identifier
     * @return renameable symbol or the reason it cannot be renamed
     */
    public RenamePreparation prepare(int offset) {
        try {
            Target target = resolve(offset);
            return new RenamePreparation.Renameable(target.id(), kindLabel(target.kind()), target.name(),
                target.caret());
        } catch (Rejection rejection) {
            return new RenamePreparation.Rejected(rejection.getMessage());
        }
    }

    /**
     * Computes and verifies the rename of the symbol at an offset.
     *
     * @param offset UTF-16 offset of, or just after, the identifier
     * @param symbol identity the rename was prepared for; the identifier at the offset must still name it
     * @param newName requested name
     * @return edit and conflicts
     */
    public RenameProposal rename(int offset, SymbolId symbol, String newName) {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(newName, "newName");
        Target target;
        try {
            target = resolve(offset);
        } catch (Rejection rejection) {
            return blocked("", newName, List.of(error(rejection.getMessage(), null)));
        }
        if (!target.id().equals(symbol))
            return blocked(target.name(), newName, List.of(error("The workspace changed since the rename was "
                + "started, and the name at the caret now refers to a different symbol", null)));

        var conflicts = new Conflicts();
        validateName(target, newName, conflicts);
        if (conflicts.blocking())
            return blocked(target.name(), newName, conflicts.list());

        Plan plan = plan(target, newName, conflicts);
        checkDeclarations(target, newName, plan, conflicts);
        if (!conflicts.blocking()) {
            verify(target, newName, plan, conflicts);
        }
        if (conflicts.blocking())
            return blocked(target.name(), newName, conflicts.list());

        unresolvedOccurrences(target, plan, conflicts);
        return new RenameProposal(target.name(), newName, edit(target, newName, plan), conflicts.list());
    }

    // region Target resolution
    private Target resolve(int offset) {
        context.checkActive();
        if (!(context.documentSnapshot() instanceof TextDocumentSnapshot source) || !sources.containsKey(source.id()))
            throw new Rejection("Only analysed Java source files can be renamed");

        SemanticIndexShard shard = graph.shards().get(source.id());
        Set<SymbolId> targets = new LinkedHashSet<>();
        TextRange caret = null;
        for (int candidate : List.of(offset, offset - 1)) {
            for (ReferenceOccurrence reference : shard.references()) {
                Range<?> range = reference.location().range();
                if (candidate < range.start || candidate >= range.end)
                    continue;
                SymbolId target = renameTarget(reference.target());
                String targetName = simpleName(target);
                if (targetName == null)
                    continue;
                for (TextRange name : directRanges(source.text(), reference, targetName, isType(target))) {
                    if (candidate >= name.start && candidate < name.end) {
                        targets.add(target);
                        caret = name;
                    }
                }
            }
            if (!targets.isEmpty())
                break;
        }

        if (targets.isEmpty()) {
            for (SymbolDescriptor symbol : shard.declarations()) {
                for (SymbolLocation location : symbol.locations()) {
                    if (location.selection() instanceof Location.TextLocation selection
                        && selection.documentId().equals(source.id()) && selection.range().start <= offset
                        && offset <= selection.range().end)
                        throw new Rejection("Renaming " + kindLabel(symbol.kind().id()) + "s is not supported");
                }
            }
            throw new Rejection(identifierAt(source.text(), offset)
                .map(name -> "Cannot resolve the symbol '" + name + "'")
                .orElse("Place the caret on the name of a symbol to rename it"));
        }
        if (targets.size() > 1)
            throw new Rejection("The name at the caret refers to several symbols");

        SymbolId id = targets.iterator().next();
        List<SymbolDescriptor> descriptors = graph.symbols(id);
        if (descriptors.isEmpty())
            throw new Rejection("The declaration of '" + simpleName(id) + "' is not available");
        SymbolDescriptor descriptor = descriptors.getFirst();
        String kind = descriptor.kind().id();
        if (descriptors.stream().anyMatch(symbol -> !symbol.kind().id().equals(kind)))
            throw new Rejection("'" + descriptor.simpleName() + "' has conflicting declarations");
        if (!TYPE_KINDS.contains(kind) && !Set.of(FIELD, METHOD, PARAMETER, LOCAL).contains(kind))
            throw new Rejection("Renaming " + kindLabel(kind) + "s is not supported");
        if (descriptor.simpleName().startsWith("<"))
            throw new Rejection("Anonymous classes have no name to rename");
        if (isRecordComponent(id))
            throw new Rejection("Renaming record components is not supported yet");

        Declaration declaration = declaration(id)
            .orElseThrow(() -> new Rejection("'" + descriptor.simpleName()
                + "' is declared in a library or generated source and cannot be renamed"));
        List<SymbolId> family = family(id, kind);
        return new Target(id, descriptor, kind, descriptor.simpleName(), declaration, family,
            Location.TextLocation.from(source, caret.start, caret.end));
    }

    private Optional<Declaration> declaration(SymbolId id) {
        Set<Declaration> found = new LinkedHashSet<>();
        for (SymbolDescriptor symbol : graph.symbols(id)) {
            for (SymbolLocation location : symbol.locations()) {
                Location<?> selection = location.selection();
                if (!(selection instanceof Location.TextLocation text))
                    continue;
                TextDocumentSnapshot snapshot = sources.get(text.documentId());
                if (snapshot != null && snapshot.uri().equals(location.uri()) && valid(text, snapshot)
                    && symbol.simpleName().equals(slice(snapshot, text.range()))) {
                    found.add(new Declaration(snapshot, text));
                }
            }
        }
        if (found.size() > 1)
            throw new Rejection("'" + simpleName(id) + "' is declared more than once");
        Optional<Declaration> declaration = found.stream().findFirst();
        declaration.ifPresent(value -> {
            ProvenanceKind provenance = value.document().origin().provenance();
            if (provenance == ProvenanceKind.GENERATED || provenance == ProvenanceKind.DECOMPILED
                || provenance == ProvenanceKind.ATTACHED_SOURCE)
                throw new Rejection("'" + simpleName(id) + "' is declared in a read-only source");
        });
        return declaration;
    }

    private List<SymbolId> family(SymbolId id, String kind) {
        if (TYPE_KINDS.contains(kind)) {
            List<SymbolId> family = new ArrayList<>(List.of(id));
            for (SemanticIndexShard shard : sourceShards()) {
                for (SymbolDescriptor symbol : shard.declarations()) {
                    if (symbol.kind().id().equals(CONSTRUCTOR) && renameTarget(symbol.id()).equals(id)) {
                        family.add(symbol.id());
                    }
                }
            }
            return List.copyOf(new LinkedHashSet<>(family));
        }
        if (!kind.equals(METHOD))
            return List.of(id);

        Set<SymbolId> family = new LinkedHashSet<>(List.of(id));
        Deque<SymbolId> pending = new ArrayDeque<>(family);
        while (!pending.isEmpty()) {
            context.checkActive();
            SymbolId next = pending.removeFirst();
            for (SymbolEdge edge : graph.outgoingEdges(next)) {
                if (edge.kind().equals(EdgeKindId.OVERRIDE) && family.add(edge.target())) {
                    pending.add(edge.target());
                }
            }
            for (SymbolEdge edge : graph.incomingEdges(next)) {
                if (edge.kind().equals(EdgeKindId.OVERRIDE) && family.add(edge.source())) {
                    pending.add(edge.source());
                }
            }
            for (InheritedImplementation pair : implementations()) {
                if (pair.implementation().equals(next) && family.add(pair.method())) {
                    pending.add(pair.method());
                }
                if (pair.method().equals(next) && family.add(pair.implementation())) {
                    pending.add(pair.implementation());
                }
            }
        }

        for (SymbolId member : family) {
            String owner = ownerName(member);
            if (declaration(member).isEmpty())
                throw new Rejection("'" + simpleName(id) + "' overrides, implements or is overridden by a method of "
                    + owner + ", which is declared in a library and cannot be renamed");
            SymbolDescriptor descriptor = graph.symbols(member).getFirst();
            boolean overrides = graph.outgoingEdges(member).stream()
                .anyMatch(edge -> edge.kind().equals(EdgeKindId.OVERRIDE));
            boolean annotated = descriptor.metadata().annotations().stream()
                .anyMatch(annotation -> Set.of("Override", "java.lang.Override").contains(annotation.name()));
            if (annotated && !overrides || overridesObjectMethod(descriptor))
                throw new Rejection("'" + simpleName(id) + "' in " + owner
                    + " overrides a method whose declaration is not available, such as a method of java.lang.Object");
        }
        return List.copyOf(family);
    }

    private List<InheritedImplementation> implementations() {
        if (implementations == null) {
            implementations = JavaOverrideGraphExtractor.inheritedImplementations(graph.shards().values(),
                sources.keySet(), context::checkActive);
        }
        return implementations;
    }

    private boolean overridesObjectMethod(SymbolDescriptor method) {
        Set<String> modifiers = method.metadata().modifiers();
        String parameters = OBJECT_METHODS.get(method.simpleName());
        return parameters != null && !modifiers.contains("static") && !modifiers.contains("private")
            && parameters.equals(parameterSignature(method.id()));
    }

    private SymbolId renameTarget(SymbolId id) {
        List<JavaSymbolIds.Component> parts = components(id);
        if (parts.size() > 2 && parts.get(parts.size() - 2).kind().equals("constructor"))
            return JavaSymbolIds.fromComponents(parts.subList(0, parts.size() - 2));
        return id;
    }
    // endregion

    // region Planning
    private Plan plan(Target target, String newName, Conflicts conflicts) {
        var plan = new Plan();
        boolean type = TYPE_KINDS.contains(target.kind());
        for (SymbolId id : target.family()) {
            for (ReferenceOccurrence reference : graph.referencesTo(id)) {
                context.checkActive();
                TextDocumentSnapshot document = documentOf(reference);
                if (document == null)
                    continue;
                List<TextRange> ranges = directRanges(document.text(), reference, target.name(), type);
                if (ranges.isEmpty()) {
                    if (!reference.kind().equals(ReferenceKindId.CALL) || !isConstructor(reference.target())) {
                        conflicts.add(error("The occurrence of '" + target.name() + "' at "
                            + describe(document, reference.location().range().start) + " cannot be updated",
                            location(document, reference.location().range())));
                    }
                    continue;
                }
                ranges.forEach(range -> plan.replace(document, range, newName));
            }
        }

        if (namedType(target.id())) {
            for (SemanticIndexShard shard : sourceShards()) {
                TextDocumentSnapshot document = sources.get(shard.documentId());
                for (ReferenceOccurrence reference : shard.references()) {
                    if (!reference.kind().equals(ReferenceKindId.IMPORT)
                        && !reference.kind().equals(ReferenceKindId.DOCUMENTATION)
                        || target.family().contains(reference.target()) || !within(reference.target(), target.id()))
                        continue;
                    qualifierRange(document, reference, target).ifPresent(range -> plan.replace(document, range,
                        newName));
                }
            }
        }

        if (target.kind().equals(PARAMETER)) {
            parameterDocumentation(target).ifPresent(range -> plan.replace(target.declaration().document(), range,
                newName));
        }
        if (annotationElement(target.descriptor())) {
            annotationArguments(target, newName, plan, conflicts);
        }

        fileRename(target, newName, plan, conflicts);
        plan.overlaps().forEach(document -> conflicts.add(error("Occurrences in " + fileName(document)
            + " overlap and cannot be renamed safely", null)));
        return plan;
    }

    /**
     * Finds the identifier a reference to a family member spells. Imports and documentation links span a qualified
     * name, of which only the segment naming the symbol is renamed.
     */
    private static List<TextRange> directRanges(String text, ReferenceOccurrence reference, String name, boolean type) {
        int start = reference.location().range().start;
        int end = reference.location().range().end;
        String region = text.substring(start, end);
        if (region.equals(name))
            return List.of(new TextRange(start, end));

        List<Segment> segments;
        if (reference.kind().equals(ReferenceKindId.IMPORT)) {
            segments = segments(text, start, end);
        } else if (reference.kind().equals(ReferenceKindId.DOCUMENTATION)) {
            int hash = region.indexOf('#');
            if (type) {
                segments = segments(text, start, hash < 0 ? end : start + hash);
            } else {
                int arguments = region.indexOf('(');
                segments = hash < 0
                    ? List.of()
                    : segments(text, start + hash + 1,
                        arguments < 0 ? end : start + arguments);
            }
        } else
            return List.of();
        if (segments.isEmpty() || !segments.getLast().text().equals(name))
            return List.of();
        return List.of(new TextRange(segments.getLast().start(), segments.getLast().end()));
    }

    /**
     * Finds the segment naming a renamed type inside an import or documentation link whose target is nested in that
     * type, such as {@code Outer} in {@code import p.Outer.Inner;}.
     */
    private Optional<TextRange> qualifierRange(
        TextDocumentSnapshot document,
        ReferenceOccurrence reference,
        Target target
    ) {
        String targetType = typeName(reference.target());
        String renamedType = typeName(target.id());
        if (targetType == null || renamedType == null)
            return Optional.empty();
        int start = reference.location().range().start;
        int end = reference.location().range().end;
        String region = document.text().substring(start, end);
        boolean member = !isType(reference.target());
        List<Segment> segments;
        if (reference.kind().equals(ReferenceKindId.IMPORT)) {
            segments = segments(document.text(), start, end);
            if (member && !segments.isEmpty()) {
                segments = segments.subList(0, segments.size() - 1);
            }
        } else {
            int hash = region.indexOf('#');
            if (member && hash < 0)
                return Optional.empty();
            segments = segments(document.text(), start, hash < 0 ? end : start + hash);
        }
        int depth = typeSegments(targetType).size() - typeSegments(renamedType).size();
        int index = segments.size() - 1 - depth;
        if (depth < 0 || index < 0 || !segments.get(index).text().equals(target.name()))
            return Optional.empty();
        return Optional.of(new TextRange(segments.get(index).start(), segments.get(index).end()));
    }

    private Optional<TextRange> parameterDocumentation(Target target) {
        SymbolId owner = target.descriptor().ownerId();
        if (owner == null || !isParameter(target.id()))
            return Optional.empty();
        TextDocumentSnapshot document = target.declaration().document();
        for (SymbolDescriptor symbol : graph.symbols(owner)) {
            for (SymbolLocation location : symbol.locations()) {
                if (!(location.declaration() instanceof Location.TextLocation declaration)
                    || !declaration.documentId().equals(document.id()) || !valid(declaration, document))
                    continue;
                String text = document.text();
                int start = declaration.range().start;
                while (start < declaration.range().end && Character.isWhitespace(text.charAt(start))) {
                    start++;
                }
                int open;
                int close;
                if (text.startsWith("/**", start)) {
                    open = start;
                    close = text.indexOf("*/", start + 3);
                } else {
                    int before = declaration.range().start;
                    while (before > 0 && Character.isWhitespace(text.charAt(before - 1))) {
                        before--;
                    }
                    close = before >= 2 && text.startsWith("*/", before - 2) ? before - 2 : -1;
                    open = close < 0 ? -1 : text.lastIndexOf("/*", close);
                    if (open < 0 || !text.startsWith("/**", open))
                        continue;
                }
                if (close < 0 || close > declaration.range().end && open >= declaration.range().start)
                    continue;
                Matcher matcher = Pattern.compile("(?m)^\\h*\\**\\h*@param\\h+(" + Pattern.quote(target.name())
                    + ")(?!\\p{javaJavaIdentifierPart})").matcher(text).region(open + 3, close);
                if (matcher.find())
                    return Optional.of(new TextRange(matcher.start(1), matcher.end(1)));
            }
        }
        return Optional.empty();
    }

    private boolean annotationElement(SymbolDescriptor symbol) {
        if (!symbol.kind().id().equals(METHOD) || symbol.ownerId() == null)
            return false;
        List<SymbolDescriptor> owners = graph.symbols(symbol.ownerId());
        return !owners.isEmpty() && owners.getFirst().kind().id().equals("java:annotation");
    }

    /**
     * Renames the element name in every usage of the renamed element's annotation type. Element names are not
     * resolved references, so they are found in the arguments of resolved annotation usages: {@code old = value}
     * pairs are renamed, and a single-element shorthand {@code @A(value)}, which names {@code value} implicitly,
     * gains an explicit element name. A usage whose annotation type is not resolved, such as an annotation nested in
     * another annotation's arguments, cannot be updated and blocks the rename if it might be of this type.
     */
    private void annotationArguments(Target target, String newName, Plan plan, Conflicts conflicts) {
        SymbolId annotation = target.descriptor().ownerId();
        String annotationName = simpleName(annotation);
        for (SemanticIndexShard shard : sourceShards()) {
            TextDocumentSnapshot document = sources.get(shard.documentId());
            if (annotationName == null || !document.text().contains(annotationName))
                continue;
            SyntaxNode root = tree(document);
            Set<Integer> resolved = new HashSet<>();
            for (ReferenceOccurrence reference : shard.references()) {
                if (!reference.kind().equals(ReferenceKindId.ANNOTATION) || documentOf(reference) == null)
                    continue;
                resolved.add(reference.location().range().start);
                if (!reference.target().equals(annotation))
                    continue;
                SyntaxNode usage = ancestor(tokenAt(root, reference.location().range().start),
                    JavaSyntaxKinds.ANNOTATION.id());
                if (usage != null) {
                    elementNames(usage, target.name(), newName, document, plan);
                }
            }
            List<SyntaxToken> tokens = tokens(root);
            for (int index = 0; index < tokens.size(); index++) {
                if (!tokens.get(index).kind().id().equals("JAVA_TOKEN_AT"))
                    continue;
                SyntaxToken name = null;
                boolean segment = true;
                for (int next = index + 1; next < tokens.size(); next++) {
                    String kind = tokens.get(next).kind().id();
                    if (trivia(kind))
                        continue;
                    if (segment && kind.equals("JAVA_TOKEN_IDENTIFIER")) {
                        name = tokens.get(next);
                        segment = false;
                    } else if (!segment && kind.equals("JAVA_TOKEN_DOT")) {
                        segment = true;
                    } else
                        break;
                }
                if (name != null && name.text().equals(annotationName) && !resolved.contains(name.start())) {
                    conflicts.add(error("The annotation at " + describe(document, name.start()) + " could not be "
                        + "resolved, so its '" + target.name() + "' element cannot be renamed safely",
                        location(document, new TextRange(name.start(), name.end()))));
                }
            }
        }
    }

    private static List<SyntaxToken> tokens(SyntaxNode root) {
        List<SyntaxToken> tokens = new ArrayList<>();
        Deque<SyntaxNode> pending = new ArrayDeque<>(List.of(root));
        while (!pending.isEmpty()) {
            SyntaxNode node = pending.removeFirst();
            if (node instanceof SyntaxToken token) {
                tokens.add(token);
            } else {
                node.children().reversed().forEach(pending::addFirst);
            }
        }
        return tokens;
    }

    private static void elementNames(
        SyntaxNode annotation,
        String oldName,
        String newName,
        TextDocumentSnapshot document,
        Plan plan
    ) {
        List<SyntaxNode> arguments = new ArrayList<>();
        int depth = 0;
        for (SyntaxNode child : annotation.children()) {
            String kind = child.kind().id();
            if (trivia(kind))
                continue;
            if (Set.of("JAVA_TOKEN_OPEN_PAREN", "JAVA_TOKEN_OPEN_BRACE", "JAVA_TOKEN_OPEN_BRACKET").contains(kind)) {
                if (depth == 1) {
                    arguments.add(child);
                }
                depth++;
            } else if (Set.of("JAVA_TOKEN_CLOSE_PAREN", "JAVA_TOKEN_CLOSE_BRACE", "JAVA_TOKEN_CLOSE_BRACKET")
                .contains(kind)) {
                if (--depth == 0)
                    break;
            } else if (depth == 1) {
                arguments.add(child);
            }
        }
        if (arguments.isEmpty())
            return;

        boolean pairs = false;
        for (int index = 0; index < arguments.size(); index++) {
            boolean elementStart = index == 0 || arguments.get(index - 1).kind().id().equals("JAVA_TOKEN_COMMA");
            if (!elementStart || index + 1 >= arguments.size()
                || !arguments.get(index).kind().id().equals("JAVA_TOKEN_IDENTIFIER")
                || !arguments.get(index + 1).kind().id().equals("JAVA_TOKEN_EQUALS"))
                continue;
            pairs = true;
            SyntaxNode name = arguments.get(index);
            if (name instanceof SyntaxToken token && token.text().equals(oldName)) {
                plan.replace(document, new TextRange(token.start(), token.end()), newName);
            }
        }
        if (!pairs && oldName.equals("value")) {
            int start = arguments.getFirst().start();
            plan.replace(document, new TextRange(start, start), newName + " = ");
        }
    }

    private static boolean trivia(String kind) {
        return kind.endsWith("WHITESPACE") || kind.endsWith("COMMENT");
    }

    private static @Nullable SyntaxNode ancestor(@Nullable SyntaxNode node, String kind) {
        for (SyntaxNode current = node; current != null; current = current.parent().orElse(null)) {
            if (current.kind().id().equals(kind))
                return current;
        }
        return null;
    }

    private void fileRename(Target target, String newName, Plan plan, Conflicts conflicts) {
        if (!TYPE_KINDS.contains(target.kind()) || !topLevel(target.descriptor()))
            return;
        TextDocumentSnapshot document = target.declaration().document();
        Optional<Path> path = document.uri().filePath();
        if (path.isEmpty() || !path.get().getFileName().toString().equals(target.name() + ".java"))
            return;

        Path renamed;
        try {
            renamed = path.get().resolveSibling(newName + ".java");
        } catch (InvalidPathException exception) {
            conflicts.add(error("The new name cannot be used as a file name", null));
            return;
        }
        DocumentUri uri = DocumentUri.fromPath(renamed);
        boolean caseOnly = path.get().equals(renamed) || sameExistingFile(path.get(), renamed);
        boolean occupied = workspace.documents().values().stream()
            .anyMatch(other -> !other.id().equals(document.id()) && sameFile(other.uri(), uri));
        if (occupied || !caseOnly && Files.exists(renamed)) {
            conflicts.add(error("The file " + renamed.getFileName() + " already exists", null));
            return;
        }
        plan.renameFile(document, uri);
    }

    /**
     * Reports whether two spellings name one existing file, as a case-only rename does on a case-insensitive
     * filesystem whose paths still compare case-sensitively.
     */
    private static boolean sameExistingFile(Path source, Path target) {
        try {
            return Files.exists(source) && Files.exists(target) && Files.isSameFile(source, target);
        } catch (IOException exception) {
            return false;
        }
    }

    private static boolean sameFile(DocumentUri first, DocumentUri second) {
        Optional<Path> one = first.filePath();
        Optional<Path> two = second.filePath();
        return one.isPresent() && two.isPresent()
            && one.get().toAbsolutePath().normalize().equals(two.get().toAbsolutePath().normalize());
    }
    // endregion

    // region Explicit checks
    private void validateName(Target target, String newName, Conflicts conflicts) {
        if (newName.equals(target.name())) {
            conflicts.add(error("The new name is the same as the current name", null));
        } else if (newName.isEmpty() || !Character.isJavaIdentifierStart(newName.codePointAt(0))
            || !newName.codePoints().allMatch(Character::isJavaIdentifierPart) || KEYWORDS.contains(newName)) {
            conflicts.add(error("'" + newName + "' is not a valid Java identifier", null));
        } else if (TYPE_KINDS.contains(target.kind()) && RESTRICTED_TYPE_NAMES.contains(newName)) {
            conflicts.add(error("'" + newName + "' cannot be used as a type name", null));
        }
    }

    private void checkDeclarations(Target target, String newName, Plan plan, Conflicts conflicts) {
        checkIdentityCollisions(target, identityMapping(target, newName), conflicts);
        if (TYPE_KINDS.contains(target.kind())) {
            checkNestedTypeNames(target, newName, conflicts);
        }
        if (Set.of(PARAMETER, LOCAL).contains(target.kind()) || isLocalType(target.id())) {
            checkScope(target, newName, conflicts);
        }
        if (target.kind().equals(METHOD)) {
            checkHierarchy(target, newName, conflicts);
            checkImplicitImplementations(target, conflicts);
            checkRecordAccessors(target, newName, conflicts);
            if (annotationElement(target.descriptor())) {
                checkAnnotationElement(target, newName, conflicts);
            }
            List<CallSite> sites = callSites(target, newName);
            checkOverloads(target, newName, sites, conflicts);
            checkUnqualifiedCalls(target, newName, sites, conflicts);
        }
    }

    /**
     * Rejects renaming an interface method that a concrete source type implements through a member the analysis
     * does not model, such as an implicit record accessor or a method inherited from {@link Enum}. Such a type
     * would no longer implement the renamed method.
     */
    private void checkImplicitImplementations(Target target, Conflicts conflicts) {
        for (SymbolId member : target.family()) {
            SymbolId owner = graph.symbols(member).getFirst().ownerId();
            List<SymbolDescriptor> owners = owner == null ? List.of() : graph.symbols(owner);
            if (owners.isEmpty() || !owners.getFirst().kind().id().equals("java:interface"))
                continue;
            for (SymbolId type : related(owner, false)) {
                List<SymbolDescriptor> types = graph.symbols(type);
                if (types.isEmpty() || !Set.of("java:class", "java:enum", "java:record")
                    .contains(types.getFirst().kind().id())
                    || types.getFirst().metadata().modifiers().contains("abstract")
                    || declarationLocation(types.getFirst()) == null)
                    continue;
                Set<SymbolId> inherited = related(type, true);
                inherited.add(type);
                boolean implemented = target.family().stream().anyMatch(candidate -> {
                    SymbolDescriptor method = graph.symbols(candidate).getFirst();
                    return inherited.contains(method.ownerId()) && !method.metadata().modifiers().contains("abstract")
                        && (method.ownerId().equals(type) || !isPrivate(method));
                });
                if (!implemented) {
                    conflicts.add(error(describeSymbol(graph, type) + " implements '" + target.name() + "' through "
                        + "a member that is not declared in source, such as a record accessor or an enum method, and "
                        + "would no longer implement it", declarationLocation(types.getFirst())));
                    return;
                }
            }
        }
    }

    /**
     * Rejects giving a record method a component's name, or renaming a method named like a component, when the
     * method takes no parameters: it would become, or stop being, the component's accessor, which record patterns
     * and implementations of interface methods use.
     */
    private void checkRecordAccessors(Target target, String newName, Conflicts conflicts) {
        for (SymbolId member : target.family()) {
            SymbolId owner = graph.symbols(member).getFirst().ownerId();
            List<SymbolDescriptor> owners = owner == null ? List.of() : graph.symbols(owner);
            if (owners.isEmpty() || !owners.getFirst().kind().id().equals("java:record")
                || !Objects.equals(parameterSignature(member), ""))
                continue;
            List<JavaSymbolIds.Component> record = components(owner);
            for (SemanticIndexShard shard : sourceShards()) {
                for (SymbolDescriptor symbol : shard.declarations()) {
                    List<JavaSymbolIds.Component> parts = components(symbol.id());
                    if (!isRecordComponent(symbol.id()) || !startsWith(parts, record)
                        || parts.subList(record.size(), parts.size()).stream()
                            .anyMatch(part -> Set.of("type", "localType", "anonymous").contains(part.kind()))
                        || !Set.of(newName, target.name()).contains(symbol.simpleName()))
                        continue;
                    conflicts.add(error("'" + symbol.simpleName() + "' is a component of record " + ownerName(member)
                        + ", so renaming '" + target.name() + "' to '" + newName + "' would change its accessor",
                        declarationLocation(symbol)));
                    return;
                }
            }
        }
    }

    /**
     * Rejects element names every annotation interface already has as methods of
     * {@link java.lang.annotation.Annotation}, and renaming the {@code value} element of an annotation that a
     * {@code @Repeatable} annotation may name as its container, which must declare {@code value}. The container's
     * class literal is not a resolved reference, so a {@code @Repeatable} argument spelling the container's simple
     * name is treated as naming it.
     */
    private void checkAnnotationElement(Target target, String newName, Conflicts conflicts) {
        if (newName.equals("annotationType")) {
            conflicts.add(error("'annotationType' is a method of java.lang.annotation.Annotation, which every "
                + "annotation interface extends", null));
            return;
        }
        if (!target.name().equals("value"))
            return;
        SymbolId container = target.descriptor().ownerId();
        for (SemanticIndexShard shard : sourceShards()) {
            TextDocumentSnapshot document = sources.get(shard.documentId());
            for (ReferenceOccurrence repeatable : shard.references()) {
                if (!repeatable.kind().equals(ReferenceKindId.ANNOTATION)
                    || !"java.lang.annotation.Repeatable".equals(typeName(repeatable.target()))
                    || documentOf(repeatable) == null)
                    continue;
                SyntaxNode usage = ancestor(tokenAt(tree(document), repeatable.location().range().start),
                    JavaSyntaxKinds.ANNOTATION.id());
                if (usage == null)
                    continue;
                String containerName = simpleName(container);
                boolean names = shard.references().stream()
                    .anyMatch(reference -> reference.target().equals(container)
                        && usage.start() <= reference.location().range().start
                        && reference.location().range().end <= usage.end())
                    || tokens(usage).stream().anyMatch(token -> token.kind().id().equals("JAVA_TOKEN_IDENTIFIER")
                        && token.text().equals(containerName));
                if (names) {
                    conflicts.add(error("'" + simpleName(container) + "' is the container of a @Repeatable "
                        + "annotation, which requires its 'value' element",
                        location(document,
                            repeatable.location().range())));
                    return;
                }
            }
        }
    }

    /**
     * Finds the method invocations and method references spelling the old or the new name, with the method each
     * one resolves to. Sites the analysis could not resolve are included with no target.
     */
    private List<CallSite> callSites(Target target, String newName) {
        List<CallSite> sites = new ArrayList<>();
        Set<String> names = Set.of(target.name(), newName);
        for (TextDocumentSnapshot document : sources.values()) {
            context.checkActive();
            if (!document.text().contains(target.name()) && !document.text().contains(newName))
                continue;
            Map<Integer, Set<SymbolId>> resolved = new HashMap<>();
            for (ReferenceOccurrence reference : graph.shards().get(document.id()).references()) {
                if (reference.kind().equals(ReferenceKindId.CALL)
                    || reference.kind().equals(ReferenceKindId.METHOD_REFERENCE)) {
                    resolved.computeIfAbsent(reference.location().range().start, _ -> new LinkedHashSet<>())
                        .add(reference.target());
                }
            }
            for (SyntaxToken token : tokens(tree(document))) {
                if (!token.kind().id().equals("JAVA_TOKEN_IDENTIFIER") || !names.contains(token.text()))
                    continue;
                Boolean unqualified = callShape(token);
                if (unqualified == null)
                    continue;
                Set<SymbolId> targets = resolved.getOrDefault(token.start(), Set.of());
                boolean reference = token.parent()
                    .filter(parent -> parent.kind().id().equals(JavaSyntaxKinds.METHOD_REFERENCE_EXPRESSION.id()))
                    .isPresent();
                sites.add(new CallSite(document, token.start(), token.text(), unqualified, reference,
                    targets.size() == 1 ? targets.iterator().next() : null));
            }
        }
        return sites;
    }

    /**
     * Reports whether an identifier names the method of an invocation or method reference: {@code true} for an
     * unqualified invocation such as {@code run()}, {@code false} for a qualified one or a method reference, and
     * {@code null} for any other identifier.
     */
    private static @Nullable Boolean callShape(SyntaxToken token) {
        SyntaxNode parent = token.parent().orElse(null);
        if (parent == null)
            return null;
        if (parent.kind().id().equals(JavaSyntaxKinds.METHOD_REFERENCE_EXPRESSION.id()))
            return meaningful(parent.children()).getLast().start() == token.start() ? false : null;
        SyntaxNode invocation = parent.parent().orElse(null);
        if (!parent.kind().id().equals(JavaSyntaxKinds.NAME_EXPRESSION.id()) || invocation == null
            || !invocation.kind().id().equals(JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION.id()))
            return null;
        List<SyntaxNode> children = meaningful(invocation.children());
        int index = children.indexOf(parent);
        if (index < 0 || index + 1 >= children.size()
            || !children.get(index + 1).kind().id().equals(JavaSyntaxKinds.ARGUMENT_LIST.id()))
            return null;
        return index == 0;
    }

    private static List<SyntaxNode> meaningful(List<? extends SyntaxNode> nodes) {
        return nodes.stream().<SyntaxNode>map(node -> node).filter(node -> !trivia(node.kind().id())).toList();
    }

    /**
     * Rejects a method rename that adds an overload existing calls could choose between. The analysis
     * approximates Java's overload resolution, so an unchanged analysed resolution does not establish that the
     * compiler would still choose the same method, or any method at all. Two overloads compete unless one of their
     * parameter positions accepts no common argument. A method reference to an overloaded name is inexact, and an
     * inexact reference no longer helps choose among the overloads of a call it is passed to, so any overload of a
     * method-referenced name competes. Identical signatures are clashes, which the hierarchy and identity checks
     * report.
     */
    private void checkOverloads(Target target, String newName, List<CallSite> sites, Conflicts conflicts) {
        for (SymbolId member : target.family()) {
            SymbolDescriptor method = graph.symbols(member).getFirst();
            SymbolId owner = method.ownerId();
            if (owner == null)
                continue;
            Set<SymbolId> types = new LinkedHashSet<>(List.of(owner));
            if (!isPrivate(method)) {
                types.addAll(related(owner, false));
            }
            String parameters = parameterSignature(member);
            for (SymbolId type : types) {
                for (Member existing : methodsNamed(type, newName, target)) {
                    context.checkActive();
                    if (existing.renamed() || parameters != null && parameters.equals(existing.parameters()))
                        continue;
                    List<CallSite> affected = sites.stream().filter(site -> site.target() == null
                        ? site.name().equals(newName)
                        : target.family().contains(site.target())
                            || methodKey(site.target()).equals(existing.key()))
                        .toList();
                    if (affected.isEmpty() || !compete(parameters, existing.parameters())
                        && affected.stream().noneMatch(CallSite::reference))
                        continue;
                    conflicts.add(error("Renaming '" + target.name() + "' in " + ownerName(member) + " to '"
                        + newName + "' would overload " + newName + "(" + Objects.requireNonNullElse(
                            existing.parameters(), "...")
                        + ") in " + existing.owner()
                        + ", and existing calls or method references could then resolve to a different method "
                        + "or become ambiguous", null));
                    return;
                }
            }
        }
    }

    /**
     * Checks invocations that name the method without a qualifier. Java does not allow an unqualified call to a
     * method named {@code yield}, and it resolves an unqualified call only among the methods of the innermost
     * enclosing type that has a method of that name, even when none of them is applicable.
     */
    private void checkUnqualifiedCalls(Target target, String newName, List<CallSite> sites, Conflicts conflicts) {
        for (CallSite site : sites) {
            context.checkActive();
            boolean renamed = site.target() != null && target.family().contains(site.target());
            if (!site.unqualified() || !renamed && !site.name().equals(newName))
                continue;
            String where = describe(site.document(), site.start());
            Location.TextLocation location = location(site.document(),
                new TextRange(site.start(), site.start() + site.name().length()));
            if (renamed && newName.equals("yield")) {
                conflicts.add(error("The call at " + where + " would become an unqualified call to 'yield', which "
                    + "Java does not allow", location));
                continue;
            }
            for (SymbolId type : enclosingTypes(site.document(), site.start())) {
                List<Member> members = methodsNamed(type, newName, target);
                if (members.isEmpty())
                    continue;
                boolean resolves = site.target() != null
                    && members.stream().anyMatch(member -> methodKey(site.target()).equals(member.key()));
                if (!resolves && (renamed || members.stream().anyMatch(Member::renamed))) {
                    conflicts.add(error("The call to '" + site.name() + "' at " + where + " would be resolved "
                        + "among the '" + newName + "' methods of " + describeSymbol(graph, type)
                        + (site.target() == null ? ", and it could not be verified" : " instead"), location));
                }
                break;
            }
        }
    }

    /**
     * Lists the types whose bodies contain an offset, innermost first. The arguments of an anonymous class's
     * instance creation lie within its declaration but outside its body, in the scope of the enclosing type.
     */
    private List<SymbolId> enclosingTypes(TextDocumentSnapshot document, int offset) {
        List<SyntaxNode> bodies = new ArrayList<>();
        for (SyntaxNode node = tokenAt(tree(document), offset); node != null; node = node.parent().orElse(null)) {
            if (TYPE_BODY_KINDS.contains(node.kind().id())) {
                bodies.add(node);
            }
        }
        Map<SymbolId, Integer> types = new HashMap<>();
        for (SymbolDescriptor symbol : graph.shards().get(document.id()).declarations()) {
            if (!TYPE_KINDS.contains(symbol.kind().id()))
                continue;
            for (SymbolLocation location : symbol.locations()) {
                if (location.declaration() instanceof Location.TextLocation declaration
                    && declaration.documentId().equals(document.id()) && declaration.range().start <= offset
                    && offset < declaration.range().end
                    && bodies.stream().anyMatch(body -> declaration.range().start <= body.start()
                        && body.end() <= declaration.range().end)) {
                    types.merge(symbol.id(), declaration.range().end - declaration.range().start, Math::min);
                }
            }
        }
        return types.entrySet().stream().sorted(Map.Entry.comparingByValue()).map(Map.Entry::getKey).toList();
    }

    /**
     * Lists the methods named {@code name} that are members of a type once the rename is applied: those it declares
     * or inherits under that name, and the renamed methods it declares or inherits.
     */
    private List<Member> methodsNamed(SymbolId type, String name, Target target) {
        return members.computeIfAbsent(new MemberQuery(type, name), _ -> membersNamed(type, name, target));
    }

    private List<Member> membersNamed(SymbolId type, String name, Target target) {
        List<SymbolId> types = new ArrayList<>(List.of(type));
        types.addAll(related(type, true));
        types.add(OBJECT);
        List<Member> members = new ArrayList<>();
        for (SymbolId owner : new LinkedHashSet<>(types)) {
            boolean own = owner.equals(type);
            for (Member member : declaredMethods(owner, name)) {
                if ((own || !member.isPrivate()) && !target.family().contains(member.key())) {
                    members.add(member);
                }
            }
            for (SymbolId renamed : target.family()) {
                SymbolDescriptor method = graph.symbols(renamed).getFirst();
                if (owner.equals(method.ownerId()) && (own || !isPrivate(method))) {
                    members.add(new Member(methodKey(renamed), ownerName(renamed), parameterSignature(renamed),
                        isPrivate(method), true));
                }
            }
        }
        return members;
    }

    private List<Member> declaredMethods(SymbolId type, String name) {
        List<SymbolDescriptor> declared = sourceMembers().get(type);
        if (declared != null)
            return declared.stream()
                .filter(symbol -> symbol.kind().id().equals(METHOD) && symbol.simpleName().equals(name))
                .map(symbol -> new Member(methodKey(symbol.id()), ownerName(symbol.id()),
                    parameterSignature(symbol.id()), isPrivate(symbol), false))
                .toList();
        String owner = typeName(type);
        if (owner == null)
            return List.of();
        List<Member> members = new ArrayList<>();
        for (var candidate : analysis().lookupMember(owner.replace('$', '.'), name)) {
            if (candidate.kind() != JavaSymbolKind.METHOD)
                continue;
            String parameters = candidateParameters(candidate);
            SymbolId key = candidate.descriptor() != null
                ? methodKey(candidate.descriptor().id())
                : parameters == null
                    ? null
                    : JavaSymbolIds.method(type, name, parameters.isEmpty()
                        ? List.of()
                        : List.of(parameters
                            .split(",")));
            members.add(new Member(key, owner.replace('$', '.'), parameters,
                candidate.descriptor() != null && isPrivate(candidate.descriptor()), false));
        }
        return members;
    }

    /** Indexes the declarations of analysed sources by their owner; every source type has an entry. */
    private Map<SymbolId, List<SymbolDescriptor>> sourceMembers() {
        if (sourceMembers == null) {
            Map<SymbolId, List<SymbolDescriptor>> members = new HashMap<>();
            for (SemanticIndexShard shard : sourceShards()) {
                for (SymbolDescriptor symbol : shard.declarations()) {
                    if (TYPE_KINDS.contains(symbol.kind().id())) {
                        members.computeIfAbsent(symbol.id(), _ -> new ArrayList<>());
                    }
                    if (symbol.ownerId() != null) {
                        members.computeIfAbsent(symbol.ownerId(), _ -> new ArrayList<>()).add(symbol);
                    }
                }
            }
            sourceMembers = members;
        }
        return sourceMembers;
    }

    /**
     * Reports whether some argument list could be applicable to both overloads. Overloads of different arity can
     * compete only through variable arity; at equal arity, a position whose types accept no common argument
     * separates them. Unknown signatures always compete.
     */
    private static boolean compete(@Nullable String first, @Nullable String second) {
        if (first == null || second == null)
            return true;
        List<String> left = first.isEmpty() ? List.of() : List.of(first.split(",", -1));
        List<String> right = second.isEmpty() ? List.of() : List.of(second.split(",", -1));
        if (left.size() != right.size())
            return variableArity(left) || variableArity(right);
        for (int index = 0; index < left.size(); index++) {
            String one = left.get(index);
            String two = right.get(index);
            boolean last = index == left.size() - 1;
            if (disjoint(one, two) && (!last || (!one.endsWith("[]") || disjoint(element(one), two))
                && (!two.endsWith("[]") || disjoint(one, element(two)))))
                return false;
        }
        return true;
    }

    private static boolean variableArity(List<String> parameters) {
        return !parameters.isEmpty() && parameters.getLast().endsWith("[]");
    }

    private static String element(String array) {
        return array.substring(0, array.length() - 2);
    }

    /**
     * Reports whether no argument converts to both erased parameter types. Any two reference types accept
     * {@code null}; a primitive and a reference type share arguments only through boxing.
     */
    private static boolean disjoint(String first, String second) {
        if (first.equals(second))
            return false;
        boolean primitive = PRIMITIVES.contains(first);
        if (primitive && PRIMITIVES.contains(second))
            return first.equals("boolean") != second.equals("boolean");
        if (primitive == PRIMITIVES.contains(second))
            return false;
        String reference = primitive ? second : first;
        return !BOXING_TYPES.contains(reference) && (reference.contains(".") || reference.endsWith("[]"));
    }

    private static boolean isPrivate(SymbolDescriptor symbol) {
        return symbol.metadata().visibility().equals("private")
            || symbol.metadata().modifiers().contains("private");
    }

    private static SymbolId methodKey(SymbolId id) {
        List<JavaSymbolIds.Component> parts = components(id);
        if (parts.isEmpty() || !parts.getLast().kind().equals("return"))
            return id;
        return JavaSymbolIds.fromComponents(parts.subList(0, parts.size() - 1));
    }

    private SyntaxNode tree(TextDocumentSnapshot document) {
        return trees.computeIfAbsent(document.id(), _ -> JavaSyntaxParser.parse(document, context::checkActive).root());
    }

    private JavaWorkspaceAnalysis analysis() {
        if (analysis == null) {
            analysis = capture.apply(context);
        }
        return analysis;
    }

    private void checkNestedTypeNames(Target target, String newName, Conflicts conflicts) {
        for (SymbolId owner = target.descriptor().ownerId(); owner != null;) {
            List<SymbolDescriptor> symbols = graph.symbols(owner);
            if (symbols.isEmpty() || !TYPE_KINDS.contains(symbols.getFirst().kind().id()))
                break;
            if (symbols.getFirst().simpleName().equals(newName)) {
                conflicts.add(error("A nested type cannot have the same name as its enclosing type '" + newName
                    + "'", null));
                return;
            }
            owner = symbols.getFirst().ownerId();
        }
        for (SemanticIndexShard shard : sourceShards()) {
            for (SymbolDescriptor symbol : shard.declarations()) {
                if (TYPE_KINDS.contains(symbol.kind().id()) && symbol.simpleName().equals(newName)
                    && encloses(target.id(), symbol)) {
                    conflicts.add(error("'" + target.name() + "' contains a nested type named '" + newName + "'",
                        declarationLocation(symbol)));
                    return;
                }
            }
        }
    }

    private boolean encloses(SymbolId type, SymbolDescriptor symbol) {
        Set<SymbolId> visited = new HashSet<>();
        for (SymbolId owner = symbol.ownerId(); owner != null && visited.add(owner);) {
            if (owner.equals(type))
                return true;
            List<SymbolDescriptor> owners = graph.symbols(owner);
            owner = owners.isEmpty() ? null : owners.getFirst().ownerId();
        }
        return false;
    }

    private void checkScope(Target target, String newName, Conflicts conflicts) {
        TextDocumentSnapshot document = target.declaration().document();
        SyntaxNode root = tree(document);
        SyntaxNode renamed = tokenAt(root, target.declaration().location().range().start);
        if (renamed == null)
            return;
        SemanticIndexShard shard = graph.shards().get(document.id());
        for (ReferenceOccurrence reference : shard.references()) {
            if (!reference.kind().equals(ReferenceKindId.DECLARATION)
                || !newName.equals(slice(document, reference.location().range())))
                continue;
            List<SymbolDescriptor> symbols = graph.symbols(reference.target());
            if (symbols.isEmpty())
                continue;
            String kind = symbols.getFirst().kind().id();
            if (!Set.of(PARAMETER, LOCAL).contains(kind) && !isLocalType(reference.target()))
                continue;
            SyntaxNode other = tokenAt(root, reference.location().range().start);
            if (other != null && sameTypeBody(renamed, other)
                && scopesOverlap(renamed, target.kind().equals(PARAMETER), other, kind.equals(PARAMETER))) {
                conflicts.add(error("'" + newName + "' is already declared in this scope at "
                    + describe(document, reference.location().range().start),
                    location(document, reference.location().range())));
                return;
            }
        }
    }

    /**
     * Reports whether either declaration lies in the other's scope. A parameter is in scope throughout its callable
     * or lambda; a local variable or local class from its declaration to the end of its enclosing block or
     * statement. Java forbids redeclaring a local name within such a scope, even in a nested block or lambda.
     */
    private static boolean scopesOverlap(
        SyntaxNode first,
        boolean firstParameter,
        SyntaxNode second,
        boolean secondParameter
    ) {
        return inScope(first, firstParameter, second.start()) || inScope(second, secondParameter, first.start());
    }

    private static boolean inScope(SyntaxNode declaration, boolean parameter, int offset) {
        SyntaxNode scope = scope(declaration);
        if (scope == null)
            return false;
        int start = parameter ? scope.start() : declaration.start();
        return offset >= start && offset < scope.end();
    }

    private static @Nullable SyntaxNode scope(SyntaxNode node) {
        for (SyntaxNode current = node.parent().orElse(null); current != null; current = current.parent()
            .orElse(null)) {
            if (SCOPE_KINDS.contains(current.kind().id()))
                return current;
        }
        return null;
    }

    private static boolean sameTypeBody(SyntaxNode first, SyntaxNode second) {
        return Objects.equals(typeBody(first), typeBody(second));
    }

    private static @Nullable SyntaxNode typeBody(SyntaxNode node) {
        for (SyntaxNode current = node.parent().orElse(null); current != null; current = current.parent()
            .orElse(null)) {
            if (TYPE_BODY_KINDS.contains(current.kind().id()))
                return current;
        }
        return null;
    }

    private void checkHierarchy(Target target, String newName, Conflicts conflicts) {
        JavaWorkspaceAnalysis analysis = analysis();
        for (SymbolId member : target.family()) {
            SymbolDescriptor method = graph.symbols(member).getFirst();
            if (method.ownerId() == null)
                continue;
            String parameters = parameterSignature(member);
            Set<SymbolId> supertypes = related(method.ownerId(), true);
            supertypes.add(OBJECT);
            Set<SymbolId> types = new LinkedHashSet<>(supertypes);
            types.addAll(related(method.ownerId(), false));
            types.remove(method.ownerId());
            for (SymbolId type : types) {
                context.checkActive();
                String owner = typeName(type);
                if (owner == null)
                    continue;
                for (var candidate : analysis.lookupMember(owner.replace('$', '.'), newName)) {
                    if (candidate.kind() != JavaSymbolKind.METHOD
                        || !Objects.equals(parameters, candidateParameters(candidate)))
                        continue;
                    SymbolDescriptor descriptor = candidate.descriptor();
                    if (supertypes.contains(type) && descriptor != null
                        && descriptor.metadata().modifiers().contains("private"))
                        continue;
                    conflicts.add(error("Renaming '" + target.name() + "' in " + ownerName(member) + " to '"
                        + newName + "' would clash with or override " + newName + "(" + parameters + ") in "
                        + owner.replace('$', '.'), descriptor == null ? null : declarationLocation(descriptor)));
                    return;
                }
            }
        }
    }

    private static @Nullable String candidateParameters(JavaProjectSemanticIndex.SymbolDescriptor candidate) {
        if (candidate.descriptor() != null)
            return parameterSignature(candidate.descriptor().id());
        String signature = candidate.signature();
        if (signature == null || !signature.startsWith("(") || !signature.endsWith(")"))
            return null;
        String content = signature.substring(1, signature.length() - 1);
        if (content.isBlank())
            return "";
        List<String> types = new ArrayList<>();
        for (String type : content.split(",")) {
            try {
                types.add(JavaSymbolIds.normalizeParameterType(type.trim()));
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
        return String.join(",", types);
    }

    private Set<SymbolId> related(SymbolId type, boolean supertypes) {
        Set<SymbolId> found = new LinkedHashSet<>();
        Deque<SymbolId> pending = new ArrayDeque<>(List.of(type));
        while (!pending.isEmpty()) {
            SymbolId next = pending.removeFirst();
            List<SymbolEdge> edges = supertypes ? graph.outgoingEdges(next) : graph.incomingEdges(next);
            for (SymbolEdge edge : edges) {
                SymbolId other = supertypes ? edge.target() : edge.source();
                if (edge.kind().equals(EdgeKindId.SUBTYPE) && found.add(other)) {
                    pending.add(other);
                }
            }
        }
        return found;
    }
    // endregion

    // region Verification
    private void verify(Target target, String newName, Plan plan, Conflicts conflicts) {
        Function<SymbolId, SymbolId> rename = identityMapping(target, newName);
        Map<DocumentId, TextDocumentSnapshot> renamedDocuments = new LinkedHashMap<>();
        Map<DocumentId, DocumentSnapshot> documents = new LinkedHashMap<>(workspace.documents());
        for (TextDocumentSnapshot document : plan.documents()) {
            DocumentUri uri = plan.renamedUri(document.id()).orElse(document.uri());
            var renamed = new TextDocumentSnapshot(document.id(), uri, document.version().next(),
                document.languageId(), document.origin(), plan.apply(document), document.encoding());
            renamedDocuments.put(document.id(), renamed);
            documents.put(document.id(), renamed);
        }
        var renamedWorkspace = new WorkspaceSnapshot(workspace.projectId(), documents, workspace.configuration(),
            workspace.revision());
        DocumentSnapshot requester = documents.get(context.documentSnapshot().id());
        var request = new FeatureRequestContext(renamedWorkspace, requester, context.cancellationToken(),
            context.deadline());
        WorkspaceSymbolGraph after = capture.apply(request).resolvedGraph(context::checkActive);

        compareReferences(after, rename, plan, renamedDocuments, conflicts);
        compareEdges(after, rename, conflicts);
        if (target.kind().equals(METHOD) || TYPE_KINDS.contains(target.kind())) {
            compareImplementations(after, rename, conflicts);
        }
    }

    /**
     * Maps each identity before the rename to the identity it has after the rename: the renamed member's name
     * component changes, and so do qualified names of a renamed type in type and parameter components.
     */
    private Function<SymbolId, SymbolId> identityMapping(Target target, String newName) {
        List<List<JavaSymbolIds.Component>> renamedMembers = target.family().stream()
            .map(JavaRenameEngine::components).toList();
        String oldTypeName = namedType(target.id()) ? components(target.id()).getLast().value() : null;
        String newType = oldTypeName == null || !oldTypeName.endsWith(target.name())
            ? null
            : oldTypeName.substring(0, oldTypeName.length() - target.name().length()) + newName;
        boolean localType = isLocalType(target.id());
        return id -> {
            List<JavaSymbolIds.Component> parts;
            try {
                parts = new ArrayList<>(JavaSymbolIds.components(id));
            } catch (IllegalArgumentException exception) {
                return id;
            }
            for (List<JavaSymbolIds.Component> member : renamedMembers) {
                if (!startsWith(parts, member))
                    continue;
                for (int index = member.size() - 1; index >= 0; index--) {
                    JavaSymbolIds.Component component = member.get(index);
                    if (NAMED_COMPONENTS.contains(component.kind()) && component.value().equals(target.name())) {
                        parts.set(index, new JavaSymbolIds.Component(component.kind(), newName));
                        break;
                    }
                }
            }
            if (newType == null && !localType)
                return JavaSymbolIds.fromComponents(parts);
            for (int index = 0; index < parts.size(); index++) {
                JavaSymbolIds.Component component = parts.get(index);
                if (component.kind().equals("type") && newType != null) {
                    parts.set(index, new JavaSymbolIds.Component("type",
                        renameType(component.value(), oldTypeName, newType)));
                } else if (component.kind().equals("parameters") && !component.value().isEmpty()) {
                    List<String> types = new ArrayList<>();
                    for (String parameter : component.value().split(",", -1)) {
                        types.add(renameParameterType(parameter, oldTypeName, newType, target.name(), newName));
                    }
                    parts.set(index, new JavaSymbolIds.Component("parameters", String.join(",", types)));
                }
            }
            return JavaSymbolIds.fromComponents(parts);
        };
    }

    /**
     * Renames the last segment of a qualified type name, or of the enclosing type of a nested type name, keeping
     * the spelling's own {@code .} or {@code $} separators.
     */
    private static String renameType(String type, String oldType, String newType) {
        String normalized = type.replace('$', '.');
        String old = oldType.replace('$', '.');
        if (!normalized.startsWith(old)
            || normalized.length() > old.length() && normalized.charAt(old.length()) != '.')
            return type;
        int nameStart = Math.max(oldType.lastIndexOf('.'), oldType.lastIndexOf('$')) + 1;
        String newName = newType.substring(Math.max(newType.lastIndexOf('.'), newType.lastIndexOf('$')) + 1);
        return type.substring(0, nameStart) + newName + type.substring(oldType.length());
    }

    private static String renameParameterType(
        String parameter,
        @Nullable String oldType,
        @Nullable String newType,
        String oldName,
        String newName
    ) {
        int arrays = parameter.indexOf('[');
        String base = arrays < 0 ? parameter : parameter.substring(0, arrays);
        String suffix = arrays < 0 ? "" : parameter.substring(arrays);
        String renamed = oldType == null || newType == null ? base : renameType(base, oldType, newType);
        if (renamed.equals(base) && base.equals(oldName)) {
            renamed = newName;
        }
        return renamed + suffix;
    }

    private void checkIdentityCollisions(Target target, Function<SymbolId, SymbolId> rename, Conflicts conflicts) {
        Map<SymbolId, List<SymbolDescriptor>> renamed = new HashMap<>();
        for (SemanticIndexShard shard : sourceShards()) {
            for (SymbolDescriptor symbol : shard.declarations()) {
                renamed.computeIfAbsent(rename.apply(symbol.id()), _ -> new ArrayList<>()).add(symbol);
            }
        }
        for (List<SymbolDescriptor> symbols : renamed.values()) {
            Set<SymbolId> distinct = new LinkedHashSet<>();
            symbols.forEach(symbol -> distinct.add(symbol.id()));
            if (distinct.size() < 2 || distinct.stream().allMatch(id -> rename.apply(id).equals(id)))
                continue;
            SymbolDescriptor existing = symbols.stream().filter(symbol -> rename.apply(symbol.id()).equals(symbol.id()))
                .findFirst().orElse(symbols.getFirst());
            conflicts.add(error("'" + existing.simpleName() + "' is already declared"
                + (existing.ownerId() == null ? "" : " in " + ownerName(existing.id())),
                declarationLocation(existing)));
        }
    }

    private void compareReferences(
        WorkspaceSymbolGraph after,
        Function<SymbolId, SymbolId> rename,
        Plan plan,
        Map<DocumentId, TextDocumentSnapshot> renamedDocuments,
        Conflicts conflicts
    ) {
        Map<ReferenceKey, Integer> expected = new LinkedHashMap<>();
        Map<ReferenceKey, ReferenceOccurrence> originals = new HashMap<>();
        for (SemanticIndexShard shard : sourceShards()) {
            for (ReferenceOccurrence reference : shard.references()) {
                context.checkActive();
                var key = new ReferenceKey(shard.documentId(),
                    plan.map(shard.documentId(), reference.location().range().start),
                    plan.map(shard.documentId(), reference.location().range().end), reference.kind(),
                    rename.apply(reference.target()));
                expected.merge(key, 1, Integer::sum);
                originals.putIfAbsent(key, reference);
            }
        }
        Map<ReferenceKey, Integer> actual = new LinkedHashMap<>();
        for (DocumentId document : sources.keySet()) {
            SemanticIndexShard shard = after.shards().get(document);
            if (shard == null) {
                conflicts.add(error(fileName(sources.get(document)) + " could not be analysed after renaming", null));
                continue;
            }
            for (ReferenceOccurrence reference : shard.references()) {
                actual.merge(new ReferenceKey(document, reference.location().range().start,
                    reference.location().range().end, reference.kind(), reference.target()), 1, Integer::sum);
            }
        }

        Map<Position, ReferenceKey> lost = new LinkedHashMap<>();
        Map<Position, ReferenceKey> gained = new LinkedHashMap<>();
        expected.forEach((key, count) -> {
            if (actual.getOrDefault(key, 0) < count) {
                lost.putIfAbsent(key.position(), key);
            }
        });
        actual.forEach((key, count) -> {
            if (expected.getOrDefault(key, 0) < count) {
                gained.putIfAbsent(key.position(), key);
            }
        });

        Set<String> reported = new HashSet<>();
        for (var entry : lost.entrySet()) {
            ReferenceKey key = entry.getValue();
            ReferenceOccurrence original = originals.get(key);
            TextDocumentSnapshot document = sources.get(key.document());
            Location.TextLocation location = location(document, original.location().range());
            ReferenceKey replacement = gained.remove(entry.getKey());
            String name = describeSymbol(graph, original.target());
            String message = replacement == null
                ? "The reference to " + name + " at " + describe(document, original.location().range().start)
                    + " would no longer resolve"
                : "The reference to " + name + " at " + describe(document, original.location().range().start)
                    + " would refer to " + describeSymbol(after, replacement.target()) + " instead";
            if (reported.add(message)) {
                conflicts.add(conflict(key.kind(), message, location));
            }
        }
        for (ReferenceKey key : gained.values()) {
            TextDocumentSnapshot document = sources.get(key.document());
            TextDocumentSnapshot renamed = renamedDocuments.getOrDefault(key.document(), document);
            int start = plan.unmap(key.document(), key.start());
            String message = "'" + slice(renamed, new TextRange(key.start(), key.end())) + "' at "
                + describe(document, start) + " would start referring to " + describeSymbol(after, key.target());
            if (reported.add(message)) {
                conflicts.add(conflict(key.kind(), message, location(document, new TextRange(start, start))));
            }
        }
    }

    private void compareEdges(WorkspaceSymbolGraph after, Function<SymbolId, SymbolId> rename, Conflicts conflicts) {
        Set<EdgeKey> expected = new LinkedHashSet<>();
        for (SemanticIndexShard shard : sourceShards()) {
            for (SymbolEdge edge : shard.edges()) {
                if (edge.kind().equals(EdgeKindId.OVERRIDE) || edge.kind().equals(EdgeKindId.SUBTYPE)) {
                    expected.add(new EdgeKey(rename.apply(edge.source()), rename.apply(edge.target()), edge.kind()));
                }
            }
        }
        Set<EdgeKey> actual = new LinkedHashSet<>();
        for (DocumentId document : sources.keySet()) {
            SemanticIndexShard shard = after.shards().get(document);
            if (shard == null)
                continue;
            for (SymbolEdge edge : shard.edges()) {
                if (edge.kind().equals(EdgeKindId.OVERRIDE) || edge.kind().equals(EdgeKindId.SUBTYPE)) {
                    actual.add(new EdgeKey(edge.source(), edge.target(), edge.kind()));
                }
            }
        }
        for (EdgeKey edge : expected) {
            if (!actual.contains(edge)) {
                conflicts.add(error(describeSymbol(after, edge.source()) + " would no longer "
                    + (edge.kind().equals(EdgeKindId.OVERRIDE) ? "override " : "extend ")
                    + describeSymbol(after, edge.target()), null));
            }
        }
        for (EdgeKey edge : actual) {
            if (!expected.contains(edge)) {
                conflicts.add(error(describeSymbol(after, edge.source()) + " would start "
                    + (edge.kind().equals(EdgeKindId.OVERRIDE) ? "overriding " : "extending ")
                    + describeSymbol(after, edge.target()), null));
            }
        }
    }
    private void compareImplementations(
        WorkspaceSymbolGraph after,
        Function<SymbolId, SymbolId> rename,
        Conflicts conflicts
    ) {
        Set<InheritedImplementation> expected = new LinkedHashSet<>();
        for (InheritedImplementation pair : implementations()) {
            expected.add(new InheritedImplementation(rename.apply(pair.implementation()),
                rename.apply(pair.method()), rename.apply(pair.type())));
        }
        Set<InheritedImplementation> actual = new LinkedHashSet<>(JavaOverrideGraphExtractor
            .inheritedImplementations(after.shards().values(), sources.keySet(), context::checkActive));
        for (InheritedImplementation pair : expected) {
            if (!actual.contains(pair)) {
                conflicts.add(error(describeSymbol(after, pair.implementation()) + " would no longer implement "
                    + describeSymbol(after, pair.method()) + " in " + describeSymbol(after, pair.type()), null));
            }
        }
        for (InheritedImplementation pair : actual) {
            if (!expected.contains(pair)) {
                conflicts.add(error(describeSymbol(after, pair.implementation()) + " would start implementing "
                    + describeSymbol(after, pair.method()) + " in " + describeSymbol(after, pair.type()), null));
            }
        }
    }
    // endregion

    // region Unresolved occurrences
    /**
     * Warns about identifiers spelling the old name that no resolved reference explains. They might refer to the
     * symbol, so they are reported rather than renamed or silently ignored.
     */
    private void unresolvedOccurrences(Target target, Plan plan, Conflicts conflicts) {
        boolean local = Set.of(PARAMETER, LOCAL).contains(target.kind()) || isLocalType(target.id());
        Collection<TextDocumentSnapshot> documents = local
            ? List.of(target.declaration().document())
            : sources.values();
        int found = 0;
        for (TextDocumentSnapshot document : documents) {
            context.checkActive();
            if (!document.text().contains(target.name()))
                continue;
            List<Range<?>> resolved = graph.shards().get(document.id()).references().stream()
                .<Range<?>>map(reference -> reference.location().range()).toList();
            Deque<SyntaxNode> pending = new ArrayDeque<>(
                List.of(tree(document)));
            while (!pending.isEmpty()) {
                SyntaxNode node = pending.removeFirst();
                if (!(node instanceof SyntaxToken token)) {
                    pending.addAll(node.children());
                    continue;
                }
                if (!token.kind().id().endsWith("IDENTIFIER") || !token.text().equals(target.name())
                    || plan.replaces(document.id(), token.start())
                    || resolved.stream().anyMatch(range -> range.start <= token.start() && token.end() <= range.end))
                    continue;
                if (++found <= REPORTED_UNRESOLVED_LIMIT) {
                    conflicts.add(new RenameConflict(RenameConflict.Severity.WARNING,
                        "'" + target.name() + "' at " + describe(document, token.start())
                            + " could not be resolved and will not be renamed",
                        location(document, new TextRange(token.start(), token.end()))));
                }
            }
        }
        if (found > REPORTED_UNRESOLVED_LIMIT) {
            conflicts.add(new RenameConflict(RenameConflict.Severity.WARNING, (found - REPORTED_UNRESOLVED_LIMIT)
                + " more unresolved occurrences of '" + target.name() + "' will not be renamed", null));
        }
    }
    // endregion

    // region Edit
    private WorkspaceEdit edit(Target target, String newName, Plan plan) {
        var builder = WorkspaceEdit.builder("Rename " + kindLabel(target.kind()) + " '" + target.name() + "' to '"
            + newName + "'");
        builder.annotation(OCCURRENCES, new ChangeAnnotation("Rename occurrences",
            "Declarations and resolved references of '" + target.name() + "'", false));
        for (TextDocumentSnapshot document : plan.documents()) {
            List<TextReplacement> replacements = plan.replacements(document.id()).stream()
                .map(replacement -> new TextReplacement(new TextRange(replacement.start(), replacement.end()),
                    replacement.text()))
                .toList();
            builder.change(new WorkspaceChange.TextEdit(document, replacements, OCCURRENCES));
        }
        plan.fileRenames().forEach((document, uri) -> {
            builder.annotation(FILE, new ChangeAnnotation("Rename file",
                "The file of a top-level type is named after it", false));
            builder.change(new WorkspaceChange.RenameFile(document, uri, FILE));
        });

        DocumentId requester = context.documentSnapshot().id();
        int start = plan.map(requester, target.caret().range().start);
        builder.select(requester, new TextSelection(start, start + newName.length()));
        return builder.build();
    }
    // endregion

    // region Helpers
    private Collection<SemanticIndexShard> sourceShards() {
        List<SemanticIndexShard> shards = new ArrayList<>();
        for (DocumentId document : sources.keySet()) {
            shards.add(graph.shards().get(document));
        }
        return shards;
    }

    private @Nullable TextDocumentSnapshot documentOf(ReferenceOccurrence reference) {
        TextDocumentSnapshot document = sources.get(reference.location().documentId());
        return document != null && reference.location() instanceof Location.TextLocation text && valid(text, document)
            ? document
            : null;
    }

    private @Nullable String simpleName(SymbolId id) {
        List<SymbolDescriptor> symbols = graph.symbols(id);
        if (!symbols.isEmpty())
            return symbols.getFirst().simpleName();
        return null;
    }

    private boolean isType(SymbolId id) {
        List<SymbolDescriptor> symbols = graph.symbols(id);
        if (!symbols.isEmpty())
            return TYPE_KINDS.contains(symbols.getFirst().kind().id());
        List<JavaSymbolIds.Component> parts = components(id);
        return !parts.isEmpty() && Set.of("type", "localType").contains(parts.getLast().kind())
            || parts.size() > 1 && parts.get(parts.size() - 2).kind().equals("localType");
    }

    private boolean isConstructor(SymbolId id) {
        List<JavaSymbolIds.Component> parts = components(id);
        return parts.size() > 2 && parts.get(parts.size() - 2).kind().equals("constructor");
    }

    private static boolean isParameter(SymbolId id) {
        List<JavaSymbolIds.Component> parts = components(id);
        return !parts.isEmpty() && parts.getLast().kind().equals("parameter");
    }

    private static boolean namedType(SymbolId id) {
        List<JavaSymbolIds.Component> parts = components(id);
        return !parts.isEmpty() && parts.getLast().kind().equals("type");
    }

    private static boolean isLocalType(SymbolId id) {
        List<JavaSymbolIds.Component> parts = components(id);
        return parts.size() > 1 && parts.get(parts.size() - 2).kind().equals("localType");
    }

    private static boolean isRecordComponent(SymbolId id) {
        return components(id).stream()
            .anyMatch(part -> part.kind().equals("local") && part.value().startsWith("JAVA_RECORD_HEADER"));
    }

    private boolean topLevel(SymbolDescriptor type) {
        if (type.ownerId() == null)
            return true;
        List<JavaSymbolIds.Component> owner = components(type.ownerId());
        return !owner.isEmpty() && owner.getLast().kind().equals("package");
    }

    private boolean within(SymbolId id, SymbolId type) {
        List<JavaSymbolIds.Component> parts = components(id);
        if (startsWith(parts, components(type)))
            return true;
        String name = typeName(type);
        String inner = typeName(id);
        if (name == null || inner == null)
            return false;
        String normalizedName = name.replace('$', '.');
        String normalizedInner = inner.replace('$', '.');
        return normalizedInner.equals(normalizedName) || normalizedInner.startsWith(normalizedName + ".");
    }

    private static @Nullable String typeName(SymbolId id) {
        for (JavaSymbolIds.Component component : components(id)) {
            if (component.kind().equals("type"))
                return component.value();
        }
        return null;
    }

    private static List<String> typeSegments(String type) {
        return List.of(type.split("[.$]"));
    }

    private static @Nullable String parameterSignature(SymbolId id) {
        for (JavaSymbolIds.Component component : components(id)) {
            if (component.kind().equals("parameters"))
                return component.value();
        }
        return null;
    }

    private String ownerName(SymbolId member) {
        List<SymbolDescriptor> symbols = graph.symbols(member);
        if (!symbols.isEmpty() && symbols.getFirst().ownerId() != null) {
            List<SymbolDescriptor> owners = graph.symbols(symbols.getFirst().ownerId());
            if (!owners.isEmpty())
                return owners.getFirst().qualifiedName() != null
                    ? owners.getFirst().qualifiedName()
                    : owners.getFirst().simpleName();
        }
        String type = typeName(member);
        return type == null ? "its type" : type.replace('$', '.');
    }

    private static List<JavaSymbolIds.Component> components(SymbolId id) {
        try {
            return JavaSymbolIds.components(id);
        } catch (IllegalArgumentException exception) {
            return List.of();
        }
    }

    private static boolean startsWith(List<JavaSymbolIds.Component> parts, List<JavaSymbolIds.Component> prefix) {
        return !prefix.isEmpty() && parts.size() >= prefix.size() && parts.subList(0, prefix.size()).equals(prefix);
    }

    private static List<Segment> segments(String text, int start, int end) {
        List<Segment> segments = new ArrayList<>();
        int index = start;
        while (index < end) {
            if (Character.isJavaIdentifierStart(text.charAt(index))) {
                int from = index;
                while (index < end && Character.isJavaIdentifierPart(text.charAt(index))) {
                    index++;
                }
                segments.add(new Segment(text.substring(from, index), from, index));
            } else if (Character.isWhitespace(text.charAt(index)) || text.charAt(index) == '.') {
                index++;
            } else
                return List.of();
        }
        return segments;
    }

    private static Optional<String> identifierAt(String text, int offset) {
        for (int candidate : List.of(offset, offset - 1)) {
            if (candidate < 0 || candidate >= text.length() || !Character.isJavaIdentifierPart(text.charAt(candidate)))
                continue;
            int start = candidate;
            while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) {
                start--;
            }
            int end = candidate;
            while (end < text.length() && Character.isJavaIdentifierPart(text.charAt(end))) {
                end++;
            }
            return Optional.of(text.substring(start, end));
        }
        return Optional.empty();
    }

    private static @Nullable SyntaxNode tokenAt(SyntaxNode node, int offset) {
        for (SyntaxNode child : node.children()) {
            if (offset >= child.start() && offset < child.end())
                return child instanceof SyntaxToken ? child : tokenAt(child, offset);
        }
        return null;
    }

    private Location.@Nullable TextLocation declarationLocation(SymbolDescriptor symbol) {
        for (SymbolLocation location : symbol.locations()) {
            if (location.selection() instanceof Location.TextLocation text) {
                TextDocumentSnapshot document = sources.get(text.documentId());
                if (document != null && valid(text, document))
                    return text;
            }
        }
        return null;
    }

    private static String describeSymbol(WorkspaceSymbolGraph graph, SymbolId id) {
        List<SymbolDescriptor> symbols = graph.symbols(id);
        if (symbols.isEmpty()) {
            String type = typeName(id);
            return type == null ? "another symbol" : "a member of " + type.replace('$', '.');
        }
        SymbolDescriptor symbol = symbols.getFirst();
        if (symbol.simpleName().startsWith("<"))
            return "an anonymous " + kindLabel(symbol.kind().id());
        String qualified = symbol.qualifiedName();
        boolean readable = qualified != null && TYPE_KINDS.contains(symbol.kind().id())
            && !qualified.contains("$local$") && !qualified.contains("$anonymous$");
        return kindLabel(symbol.kind().id()) + " '" + (readable ? qualified : symbol.simpleName()) + "'";
    }

    private static String kindLabel(String kind) {
        return switch (kind) {
            case "java:class" -> "class";
            case "java:interface" -> "interface";
            case "java:enum" -> "enum";
            case "java:record" -> "record";
            case "java:annotation" -> "annotation";
            case FIELD -> "field";
            case METHOD -> "method";
            case CONSTRUCTOR -> "constructor";
            case PARAMETER -> "parameter";
            case LOCAL -> "local variable";
            case "java:type_parameter" -> "type parameter";
            case "java:package" -> "package";
            case "java:module" -> "module";
            default -> kind.substring(kind.indexOf(':') + 1).replace('_', ' ');
        };
    }

    private static RenameConflict conflict(ReferenceKindId kind, String message, Location.TextLocation location) {
        return new RenameConflict(kind.equals(ReferenceKindId.DOCUMENTATION)
            ? RenameConflict.Severity.WARNING
            : RenameConflict.Severity.ERROR, message, location);
    }

    private static RenameConflict error(String message, Location.@Nullable TextLocation location) {
        return new RenameConflict(RenameConflict.Severity.ERROR, message, location);
    }

    private static RenameProposal blocked(String oldName, String newName, List<RenameConflict> conflicts) {
        return new RenameProposal(oldName, newName, null, conflicts);
    }

    private static boolean valid(Location.TextLocation location, TextDocumentSnapshot snapshot) {
        try {
            location.validateAgainst(snapshot);
            return true;
        } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
            return false;
        }
    }

    private static String slice(TextDocumentSnapshot document, Range<?> range) {
        return document.text().substring(range.start, range.end);
    }

    private static Location.TextLocation location(TextDocumentSnapshot document, Range<?> range) {
        return Location.TextLocation.from(document, range.start, range.end);
    }

    private static String describe(TextDocumentSnapshot document, int offset) {
        return fileName(document) + ":" + (document.lineMap().positionAt(offset).line() + 1);
    }

    private static String fileName(TextDocumentSnapshot document) {
        return document.uri().fileName().orElse(document.uri().toString());
    }
    // endregion

    private record Target(
        SymbolId id,
        SymbolDescriptor descriptor,
        String kind,
        String name,
        Declaration declaration,
        List<SymbolId> family,
        Location.TextLocation caret
    ) {
    }

    private record Declaration(TextDocumentSnapshot document, Location.TextLocation location) {
    }

    private record Segment(String text, int start, int end) {
    }

    private record ReferenceKey(DocumentId document, int start, int end, ReferenceKindId kind, SymbolId target) {
        private Position position() {
            return new Position(document, start, end, kind);
        }
    }

    private record Position(DocumentId document, int start, int end, ReferenceKindId kind) {
    }

    private record EdgeKey(SymbolId source, SymbolId target, EdgeKindId kind) {
    }

    private record MemberQuery(SymbolId type, String name) {
    }

    /**
     * An invocation or method reference spelling the old or new name.
     *
     * @param reference whether the site is a method reference
     * @param target method it resolves to, or {@code null} when unresolved or ambiguous
     */
    private record CallSite(
        TextDocumentSnapshot document,
        int start,
        String name,
        boolean unqualified,
        boolean reference,
        @Nullable SymbolId target
    ) {
    }

    /**
     * A method that is a member of a type once the rename is applied.
     *
     * @param key identity without any return-type component, or {@code null} when unknown
     * @param parameters erased parameter types, or {@code null} when unknown
     * @param renamed whether the method is one of the renamed methods
     */
    private record Member(
        @Nullable SymbolId key,
        String owner,
        @Nullable String parameters,
        boolean isPrivate,
        boolean renamed
    ) {
    }

    /** Planned replacements and file renames, with offset mapping between the old and new texts. */
    private static final class Plan {
        private final Map<DocumentId, TextDocumentSnapshot> documents = new LinkedHashMap<>();
        private final Map<DocumentId, TreeMap<Integer, Replacement>> replacements = new HashMap<>();
        private final Map<TextDocumentSnapshot, DocumentUri> fileRenames = new LinkedHashMap<>();
        private final Set<DocumentId> overlapping = new LinkedHashSet<>();

        private void replace(TextDocumentSnapshot document, TextRange range, String text) {
            documents.putIfAbsent(document.id(), document);
            TreeMap<Integer, Replacement> planned = replacements.computeIfAbsent(document.id(), _ -> new TreeMap<>());
            var replacement = new Replacement(range.start, range.end, text);
            Replacement existing = planned.get(range.start);
            if (replacement.equals(existing))
                return;
            var before = planned.floorEntry(range.start);
            var after = planned.ceilingEntry(range.start);
            if (existing != null || before != null && before.getValue().end() > range.start
                || after != null && after.getKey() < range.end) {
                overlapping.add(document.id());
            }
            planned.put(range.start, replacement);
        }

        private void renameFile(TextDocumentSnapshot document, DocumentUri uri) {
            documents.putIfAbsent(document.id(), document);
            fileRenames.put(document, uri);
        }

        private Collection<TextDocumentSnapshot> documents() {
            return documents.values().stream()
                .sorted(Comparator.comparing(document -> document.uri().toString()))
                .toList();
        }

        private List<Replacement> replacements(DocumentId document) {
            return List.copyOf(replacements.getOrDefault(document, new TreeMap<>()).values());
        }

        private Map<TextDocumentSnapshot, DocumentUri> fileRenames() {
            return fileRenames;
        }

        private Optional<DocumentUri> renamedUri(DocumentId document) {
            return fileRenames.entrySet().stream().filter(entry -> entry.getKey().id().equals(document))
                .map(Map.Entry::getValue).findFirst();
        }

        private List<TextDocumentSnapshot> overlaps() {
            return overlapping.stream().map(documents::get).toList();
        }

        private boolean replaces(DocumentId document, int offset) {
            return replacements.getOrDefault(document, new TreeMap<>()).values().stream()
                .anyMatch(replacement -> replacement.start() <= offset && offset < replacement.end());
        }

        private String apply(TextDocumentSnapshot document) {
            var text = new StringBuilder(document.text());
            for (Replacement replacement : replacements(document.id()).reversed()) {
                text.replace(replacement.start(), replacement.end(), replacement.text());
            }
            return text.toString();
        }

        /**
         * Maps an offset of the old text to the new text. Replacements ending at or before the offset shift it, so a
         * range starting at a replaced name keeps its start and a range ending after it moves its end.
         */
        private int map(DocumentId document, int offset) {
            int shift = 0;
            for (Replacement replacement : replacements(document)) {
                if (replacement.end() <= offset) {
                    shift += replacement.text().length() - (replacement.end() - replacement.start());
                }
            }
            return offset + shift;
        }

        private int unmap(DocumentId document, int offset) {
            int shift = 0;
            for (Replacement replacement : replacements(document)) {
                int end = replacement.start() + shift + replacement.text().length();
                if (end > offset)
                    break;
                shift += replacement.text().length() - (replacement.end() - replacement.start());
            }
            return offset - shift;
        }
    }

    private record Replacement(int start, int end, String text) {
    }

    private static final class Conflicts {
        private final List<RenameConflict> conflicts = new ArrayList<>();
        private final Set<String> messages = new HashSet<>();

        private void add(RenameConflict conflict) {
            if (messages.add(conflict.severity() + conflict.message())) {
                conflicts.add(conflict);
            }
        }

        private boolean blocking() {
            return conflicts.stream().anyMatch(conflict -> conflict.severity() == RenameConflict.Severity.ERROR);
        }

        private List<RenameConflict> list() {
            return List.copyOf(conflicts);
        }
    }

    /** Raised while resolving the target to reject the rename with a user-facing reason. */
    private static final class Rejection extends RuntimeException {
        private Rejection(String message) {
            super(message, null, false, false);
        }
    }
}
