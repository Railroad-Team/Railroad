package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.diagnostics.EditorDiagnostic.TextEditorDiagnostic;
import dev.railroadide.railroad.ide.language.impl.index.JavaWorkspaceAnalysis;
import dev.railroadide.railroad.ide.sst.document.api.TextDocumentSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.TextRange;
import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceEdit;
import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolKind;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticIndex;
import dev.railroadide.railroad.ide.sst.project.JavaSymbolIndex;
import dev.railroadide.railroad.ide.sst.provider.api.CodeAction;
import dev.railroadide.railroad.ide.sst.provider.api.CodeActionRequest;
import dev.railroadide.railroad.ide.sst.request.api.FeatureRequestContext;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken;
import dev.railroadide.railroad.plugin.spi.editor.TextReplacement;
import org.jspecify.annotations.Nullable;

import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.util.*;
import java.util.function.Function;

/**
 * Java quick fixes for imports: add an import for an unresolved simple type name, remove an unused import and
 * remove a duplicate import.
 * <p>
 * Fixes are verified against the captured document and workspace, never derived from diagnostics alone. An import
 * is added only when the name provably resolves to no type in the file, and exactly one accessible top-level type
 * of that name exists in the captured workspace; when several exist, an unavailable action names them instead.
 * Types in JDK packages that no system module exports to all code are not candidates, since no source file can
 * refer to them. Edits are computed only on resolution.
 */
public final class JavaCodeActions {
    /** Diagnostic code of an unused import. */
    public static final String UNUSED_IMPORT = "SEM_UNUSED_IMPORT";
    /** Diagnostic code of a repeated import. */
    public static final String DUPLICATE_IMPORT = "SEM_DUPLICATE_IMPORT";
    private static final Set<String> UNRESOLVED_CODES = Set.of("SEM_UNRESOLVED_TYPE", "SEM_UNRESOLVED_NAME");
    private static final String ADD_IMPORT = "java:add-import:";
    private static final String REMOVE_UNUSED = "java:remove-unused-import:";
    private static final String REMOVE_DUPLICATE = "java:remove-duplicate-import:";
    private static final String IDENTIFIER = "JAVA_TOKEN_IDENTIFIER";
    private static final String TYPE_REFERENCE = "JAVA_TYPE_REFERENCE";
    private static final String NAME_EXPRESSION = "JAVA_NAME_EXPRESSION";
    private static final String QUALIFIED_NAME = "JAVA_QUALIFIED_NAME";
    private static final String ANNOTATION = "JAVA_ANNOTATION";
    private static final Set<String> SKIPPED_DECLARATIONS = Set.of("JAVA_PACKAGE_DECLARATION",
        "JAVA_IMPORT_DECLARATION");
    private static final Set<String> INHERITING = Set.of("JAVA_EXTENDS_CLAUSE", "JAVA_IMPLEMENTS_CLAUSE",
        "JAVA_ANONYMOUS_CLASS_BODY", "JAVA_ENUM_DECLARATION", "JAVA_RECORD_DECLARATION");
    private static final Set<JavaSymbolKind> TYPE_KINDS = EnumSet.of(JavaSymbolKind.CLASS, JavaSymbolKind.INTERFACE,
        JavaSymbolKind.ENUM, JavaSymbolKind.RECORD, JavaSymbolKind.ANNOTATION);
    private static final int LISTED_CANDIDATES = 4;

    private final FeatureRequestContext context;
    private final Function<FeatureRequestContext, JavaWorkspaceAnalysis> capture;
    private final @Nullable TextDocumentSnapshot snapshot;
    private @Nullable SyntaxNode root;
    private @Nullable Names names;
    private @Nullable JavaSymbolIndex symbols;

    /**
     * Binds the engine to one captured request.
     *
     * @param context captured document and workspace
     * @param capture Java analysis of a captured workspace, used to look up types
     */
    public JavaCodeActions(
        FeatureRequestContext context,
        Function<FeatureRequestContext, JavaWorkspaceAnalysis> capture
    ) {
        this.context = Objects.requireNonNull(context, "context");
        this.capture = Objects.requireNonNull(capture, "capture");
        this.snapshot = context.documentSnapshot() instanceof TextDocumentSnapshot text
            && "java".equals(text.languageId()) ? text : null;
    }

    /**
     * Discovers the import fixes touching the requested range or its diagnostics.
     *
     * @param request caret or selection and nearby diagnostics
     * @return deferred and unavailable actions, preferred fixes first
     */
    public List<CodeAction> discover(CodeActionRequest request) {
        Objects.requireNonNull(request, "request");
        if (snapshot == null)
            return List.of();
        List<TextEditorDiagnostic> diagnostics = currentDiagnostics(request.diagnostics());
        List<CodeAction> actions = new ArrayList<>();
        discoverImportRemovals(request, diagnostics, actions);
        discoverMissingImports(request, diagnostics, actions);
        context.checkActive();
        return actions.stream().sorted(Comparator.comparing(action -> !action.preferred())).toList();
    }

    /**
     * Verifies a deferred action again and computes its edit.
     *
     * @param action action returned by {@link #discover} for the same document revision
     * @return the action with its edit, or the action marked unavailable when it no longer applies
     */
    public CodeAction resolve(CodeAction action) {
        Objects.requireNonNull(action, "action");
        String data = action.data();
        if (!action.available() || action.isResolved())
            return action;
        if (snapshot == null || data == null)
            return action.asUnavailable("Unsupported action");
        if (data.startsWith(REMOVE_UNUSED))
            return resolveRemoval(action, data.substring(REMOVE_UNUSED.length()),
                JavaImports.unusedImports(root(), context::checkActive), "The import is used");
        if (data.startsWith(REMOVE_DUPLICATE))
            return resolveRemoval(action, data.substring(REMOVE_DUPLICATE.length()),
                JavaImports.duplicateImports(root()), "The import is no longer repeated");
        if (data.startsWith(ADD_IMPORT))
            return resolveAddition(action, data.substring(ADD_IMPORT.length()));
        return action.asUnavailable("Unsupported action");
    }

    private void discoverImportRemovals(
        CodeActionRequest request,
        List<TextEditorDiagnostic> diagnostics,
        List<CodeAction> actions
    ) {
        List<JavaImports.ImportDeclaration> imports = JavaImports.imports(root());
        if (imports.stream().noneMatch(declaration -> touches(request, diagnostics, declaration)))
            return;
        Set<Integer> duplicates = new HashSet<>();
        JavaImports.duplicateImports(root()).forEach(declaration -> duplicates.add(declaration.start()));
        Set<Integer> unused = new HashSet<>();
        JavaImports.unusedImports(root(), context::checkActive)
            .forEach(declaration -> unused.add(declaration.start()));
        for (JavaImports.ImportDeclaration declaration : imports) {
            context.checkActive();
            if (!touches(request, diagnostics, declaration))
                continue;
            String shown = (declaration.isStatic() ? "static " : "") + declaration.target();
            if (duplicates.contains(declaration.start())) {
                actions.add(CodeAction.deferred("Remove duplicate import '" + shown + "'",
                    REMOVE_DUPLICATE + declaration.start())
                    .withDiagnostics(fixed(diagnostics, DUPLICATE_IMPORT, declaration))
                    .asPreferred());
            } else if (unused.contains(declaration.start())) {
                actions.add(CodeAction.deferred("Remove unused import '" + shown + "'",
                    REMOVE_UNUSED + declaration.start())
                    .withDiagnostics(fixed(diagnostics, UNUSED_IMPORT, declaration))
                    .asPreferred());
            }
        }
    }

    private void discoverMissingImports(
        CodeActionRequest request,
        List<TextEditorDiagnostic> diagnostics,
        List<CodeAction> actions
    ) {
        Map<String, Boolean> touched = new LinkedHashMap<>();
        for (Reference reference : names().references()) {
            if (request.touches(reference.start(), reference.end()) || diagnostics.stream()
                .filter(diagnostic -> UNRESOLVED_CODES.contains(diagnostic.code()))
                .anyMatch(diagnostic -> diagnostic.location().range().start <= reference.start()
                    && reference.end() <= diagnostic.location().range().end)) {
                touched.merge(reference.name(), reference.annotation(), Boolean::logicalAnd);
            }
        }
        for (Map.Entry<String, Boolean> reference : touched.entrySet()) {
            context.checkActive();
            String name = reference.getKey();
            boolean annotation = reference.getValue();
            Candidates candidates = candidates(name, annotation);
            if (candidates == null || candidates.names().isEmpty())
                continue;
            List<TextEditorDiagnostic> fixed = diagnostics.stream()
                .filter(diagnostic -> UNRESOLVED_CODES.contains(diagnostic.code()))
                .filter(diagnostic -> names().references().stream().anyMatch(other -> other.name()
                    .equals(name) && diagnostic.location().range().start <= other.start()
                    && other.end() <= diagnostic.location().range().end))
                .toList();
            if (candidates.names().size() > 1) {
                actions.add(CodeAction.unavailable("Import '" + name + "'",
                    "'" + name + "' is ambiguous: " + listed(candidates.names()))
                    .withDiagnostics(fixed));
            } else if (!candidates.accessible()) {
                actions.add(CodeAction.unavailable("Import '" + candidates.names().getFirst() + "'",
                    "'" + candidates.names().getFirst() + "' is not accessible from this file")
                    .withDiagnostics(fixed));
            } else {
                actions.add(CodeAction.deferred("Import '" + candidates.names().getFirst() + "'",
                    ADD_IMPORT + (annotation ? "@" : "") + candidates.names().getFirst())
                    .withDiagnostics(fixed)
                    .asPreferred());
            }
        }
    }

    private CodeAction resolveRemoval(
        CodeAction action,
        String offset,
        List<JavaImports.ImportDeclaration> removable,
        String reason
    ) {
        int start;
        try {
            start = Integer.parseInt(offset);
        } catch (NumberFormatException exception) {
            return action.asUnavailable("Unsupported action");
        }
        for (JavaImports.ImportDeclaration declaration : removable) {
            if (declaration.start() != start)
                continue;
            TextRange range = removalRange(declaration);
            return action.withEdit(WorkspaceEdit.builder(action.title())
                .replace(snapshot, new TextReplacement(range, ""))
                .build());
        }
        return action.asUnavailable(reason);
    }

    private CodeAction resolveAddition(CodeAction action, String encoded) {
        boolean annotation = encoded.startsWith("@");
        String qualifiedName = annotation ? encoded.substring(1) : encoded;
        String name = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
        boolean referenced = names().references().stream()
            .anyMatch(reference -> reference.name().equals(name) && (!annotation || reference.annotation()));
        Candidates candidates = referenced ? candidates(name, annotation) : null;
        if (candidates == null || !candidates.names().equals(List.of(qualifiedName)) || !candidates.accessible())
            return action.asUnavailable("'" + name + "' no longer needs this import");
        return action.withEdit(WorkspaceEdit.builder(action.title())
            .replace(snapshot, insertion(qualifiedName))
            .build());
    }

    /**
     * Finds the types an import could make {@code name} refer to, or {@code null} when the name may already refer
     * to something in this file, so that adding an import is not provably a fix.
     */
    private @Nullable Candidates candidates(String name, boolean annotation) {
        if (names().declared().contains(name))
            return null;
        List<JavaImports.ImportDeclaration> imports = JavaImports.imports(root());
        if (imports.stream().anyMatch(declaration -> !declaration.isWildcard()
            && declaration.importedName().equals(name)))
            return null;

        JavaSymbolIndex index = symbols();
        List<JavaProjectSemanticIndex.SymbolDescriptor> named = index.lookupSimpleName(name);
        context.checkActive();
        boolean nestedOrField = named.stream().anyMatch(symbol -> symbol.kind() == JavaSymbolKind.FIELD
            || symbol.kind() instanceof JavaSymbolKind kind && TYPE_KINDS.contains(kind) && !symbol.isTopLevel());
        if (isType(index, "java.lang." + name) || isType(index, qualify(names().packageName(), name)))
            return null;
        if (nestedOrField && names().inherits())
            return null;
        for (JavaImports.ImportDeclaration declaration : imports) {
            if (!declaration.isWildcard())
                continue;
            String owner = declaration.owner();
            if (declaration.isStatic()) {
                if (nestedOrField)
                    return null;
                continue;
            }
            if (isType(index, owner + "." + name))
                return null;
            boolean ownerIsType = isType(index, owner);
            if (!ownerIsType && !index.containsPackage(owner))
                return null;
            if (ownerIsType && nestedOrField)
                return null;
        }

        Map<String, JavaProjectSemanticIndex.SymbolDescriptor> types = new TreeMap<>();
        for (JavaProjectSemanticIndex.SymbolDescriptor symbol : named) {
            if (!(symbol.kind() instanceof JavaSymbolKind kind) || !TYPE_KINDS.contains(kind) || !symbol.isTopLevel()
                || annotation && kind != JavaSymbolKind.ANNOTATION)
                continue;
            String qualifiedName = symbol.qualifiedName();
            if (qualifiedName == null || qualifiedName.indexOf('.') < 0 || qualifiedName.indexOf('$') >= 0
                || !qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1).equals(name)
                || JdkPackages.hidden(qualifiedName.substring(0, qualifiedName.lastIndexOf('.'))))
                continue;
            types.merge(qualifiedName, symbol, (first, second) -> accessible(first) ? first : second);
        }
        boolean accessible = types.size() == 1 && accessible(types.values().iterator().next());
        return new Candidates(List.copyOf(types.keySet()), accessible);
    }

    private static boolean accessible(JavaProjectSemanticIndex.SymbolDescriptor symbol) {
        return symbol.descriptor() != null && "public".equals(symbol.descriptor().metadata().visibility());
    }

    /** Packages of the JDK's system modules that no module exports to all code. */
    private static final class JdkPackages {
        private static final Set<String> HIDDEN = hiddenPackages();

        private static boolean hidden(String packageName) {
            return HIDDEN.contains(packageName);
        }

        private static Set<String> hiddenPackages() {
            Set<String> packages = new HashSet<>();
            Set<String> exported = new HashSet<>();
            for (ModuleReference module : ModuleFinder.ofSystem().findAll()) {
                packages.addAll(module.descriptor().packages());
                module.descriptor().exports().stream()
                    .filter(export -> !export.isQualified())
                    .forEach(export -> exported.add(export.source()));
            }
            packages.removeAll(exported);
            return Set.copyOf(packages);
        }
    }

    private static boolean isType(JavaSymbolIndex index, String qualifiedName) {
        return index.lookupQualifiedName(qualifiedName).stream()
            .anyMatch(symbol -> symbol.kind() instanceof JavaSymbolKind kind && TYPE_KINDS.contains(kind));
    }

    private TextReplacement insertion(String qualifiedName) {
        String text = snapshot.text();
        String newline = text.contains("\r\n") ? "\r\n" : "\n";
        String statement = "import " + qualifiedName + ";";
        List<JavaImports.ImportDeclaration> imports = JavaImports.imports(root());
        List<JavaImports.ImportDeclaration> plain = imports.stream()
            .filter(declaration -> !declaration.isStatic()).toList();
        for (JavaImports.ImportDeclaration declaration : plain) {
            if (declaration.target().compareTo(qualifiedName) > 0)
                return before(text, declaration.start(), statement, newline);
        }
        if (!plain.isEmpty())
            return insertAt(afterTrailingComment(text, plain.getLast().end()), newline + statement);
        if (!imports.isEmpty())
            return before(text, imports.getFirst().start(), statement, newline);
        int packageEnd = names().packageEnd();
        if (packageEnd >= 0)
            return insertAt(packageEnd, newline + newline + statement);
        int first = names().firstDeclaration();
        int lineStart = text.lastIndexOf('\n', first - 1) + 1;
        return text.substring(lineStart, first).isBlank()
            ? insertAt(lineStart, statement + newline + newline)
            : insertAt(first, statement + " ");
    }

    private static int afterTrailingComment(String text, int end) {
        int newline = text.indexOf('\n', end);
        int lineEnd = newline < 0 ? text.length() : newline > 0 && text.charAt(newline - 1) == '\r'
            ? newline - 1
            : newline;
        String rest = text.substring(end, lineEnd).strip();
        return rest.isEmpty() || rest.startsWith("//") ? lineEnd : end;
    }

    private static TextReplacement before(String text, int start, String statement, String newline) {
        int lineStart = text.lastIndexOf('\n', start - 1) + 1;
        return text.substring(lineStart, start).isBlank()
            ? insertAt(lineStart, statement + newline)
            : insertAt(start, statement + " ");
    }

    private static TextReplacement insertAt(int offset, String text) {
        return new TextReplacement(new TextRange(offset, offset), text);
    }

    private TextRange removalRange(JavaImports.ImportDeclaration declaration) {
        String text = snapshot.text();
        int lineStart = text.lastIndexOf('\n', declaration.start() - 1) + 1;
        int newline = text.indexOf('\n', declaration.end());
        int lineEnd = newline < 0 ? text.length() : newline;
        if (text.substring(lineStart, declaration.start()).isBlank()
            && text.substring(declaration.end(), lineEnd).isBlank())
            return new TextRange(lineStart, newline < 0 ? text.length() : newline + 1);
        int end = declaration.end();
        while (end < lineEnd && (text.charAt(end) == ' ' || text.charAt(end) == '\t')) {
            end++;
        }
        return new TextRange(declaration.start(), end);
    }

    private List<TextEditorDiagnostic> currentDiagnostics(List<TextEditorDiagnostic> diagnostics) {
        List<TextEditorDiagnostic> current = new ArrayList<>();
        for (TextEditorDiagnostic diagnostic : diagnostics) {
            try {
                diagnostic.location().validateAgainst(snapshot);
                current.add(diagnostic);
            } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
                // Diagnostics of another revision say nothing about this one.
            }
        }
        return List.copyOf(current);
    }

    private static boolean touches(
        CodeActionRequest request,
        List<TextEditorDiagnostic> diagnostics,
        JavaImports.ImportDeclaration declaration
    ) {
        return request.touches(declaration.start(), declaration.end()) || diagnostics.stream()
            .filter(diagnostic -> UNUSED_IMPORT.equals(diagnostic.code())
                || DUPLICATE_IMPORT.equals(diagnostic.code()))
            .anyMatch(diagnostic -> within(diagnostic, declaration));
    }

    private static List<TextEditorDiagnostic> fixed(
        List<TextEditorDiagnostic> diagnostics,
        String code,
        JavaImports.ImportDeclaration declaration
    ) {
        return diagnostics.stream()
            .filter(diagnostic -> code.equals(diagnostic.code()) && within(diagnostic, declaration))
            .toList();
    }

    private static boolean within(TextEditorDiagnostic diagnostic, JavaImports.ImportDeclaration declaration) {
        TextRange range = diagnostic.location().range();
        return range.start >= declaration.start() && range.end <= declaration.end();
    }

    private static String listed(List<String> candidates) {
        String shown = String.join(", ", candidates.subList(0, Math.min(LISTED_CANDIDATES, candidates.size())));
        return candidates.size() > LISTED_CANDIDATES
            ? shown + " and " + (candidates.size() - LISTED_CANDIDATES) + " more"
            : shown;
    }

    private static String qualify(String packageName, String name) {
        return packageName.isEmpty() ? name : packageName + "." + name;
    }

    private SyntaxNode root() {
        if (root == null) {
            root = JavaSyntaxParser.parse(Objects.requireNonNull(snapshot), context::checkActive).root();
        }
        return root;
    }

    private JavaSymbolIndex symbols() {
        if (symbols == null) {
            symbols = capture.apply(context).symbols();
        }
        return symbols;
    }

    private Names names() {
        if (names == null) {
            names = Names.collect(root(), context::checkActive);
        }
        return names;
    }

    private record Candidates(List<String> names, boolean accessible) {
    }

    /**
     * A simple name used where an import could make it refer to a type: the first identifier of a type reference,
     * the qualifier of a member access or method reference, or the name of an annotation.
     */
    private record Reference(String name, int start, int end, boolean annotation) {
    }

    /**
     * Names of one compilation unit: importable references, names declared anywhere in it and whether any of its
     * types inherits members.
     */
    private record Names(
        List<Reference> references,
        Set<String> declared,
        boolean inherits,
        String packageName,
        int packageEnd,
        int firstDeclaration
    ) {
        private static Names collect(SyntaxNode root, Runnable check) {
            List<Reference> references = new ArrayList<>();
            Set<String> declared = new HashSet<>();
            boolean inherits = false;
            String packageName = "";
            int packageEnd = -1;
            int firstDeclaration = -1;
            int documentation = -1;
            List<SyntaxToken> tokens = new ArrayList<>();
            for (SyntaxNode child : root.children()) {
                if (child.kind().id().equals("JAVA_PACKAGE_DECLARATION")) {
                    packageEnd = child.end();
                    packageName = packageName(child);
                }
            }
            Deque<SyntaxNode> pending = new ArrayDeque<>(root.children());
            int visited = 0;
            while (!pending.isEmpty()) {
                if ((++visited & 255) == 0) {
                    check.run();
                }
                SyntaxNode node = pending.removeFirst();
                String kind = node.kind().id();
                if (SKIPPED_DECLARATIONS.contains(kind))
                    continue;
                inherits |= INHERITING.contains(kind);
                if (node instanceof SyntaxToken token) {
                    if (firstDeclaration < 0 && kind.equals("JAVA_TOKEN_JAVADOC_COMMENT")) {
                        documentation = token.start();
                    } else if (firstDeclaration < 0 && !kind.equals("JAVA_TOKEN_WHITESPACE")
                        && !kind.endsWith("_COMMENT") && !kind.equals("JAVA_TOKEN_EOF")) {
                        firstDeclaration = documentation >= 0 ? documentation : token.start();
                    }
                    tokens.add(token);
                    continue;
                }
                node.children().reversed().forEach(pending::addFirst);
            }
            for (int index = 0; index < tokens.size(); index++) {
                SyntaxToken token = tokens.get(index);
                if (!token.kind().id().equals(IDENTIFIER))
                    continue;
                SyntaxNode parent = token.parent().orElse(null);
                String parentKind = parent == null ? "" : parent.kind().id();
                switch (parentKind) {
                    case TYPE_REFERENCE -> {
                        if (firstIdentifier(parent) == token) {
                            references.add(new Reference(token.text(), token.start(), token.end(), false));
                        }
                    }
                    case NAME_EXPRESSION -> {
                        if (qualifier(tokens, index)) {
                            references.add(new Reference(token.text(), token.start(), token.end(), false));
                        }
                    }
                    case QUALIFIED_NAME -> {
                        if (parent.parent().filter(grand -> grand.kind().id().equals(ANNOTATION)).isEmpty()) {
                            declared.add(token.text());
                        } else if (firstIdentifier(parent) == token && significant(tokens, index, 1)
                            .filter(next -> next.kind().id().equals("JAVA_TOKEN_DOT")).isEmpty()) {
                            references.add(new Reference(token.text(), token.start(), token.end(), true));
                        }
                    }
                    case ANNOTATION -> {
                    }
                    default -> declared.add(token.text());
                }
            }
            check.run();
            return new Names(List.copyOf(references), Set.copyOf(declared), inherits, packageName, packageEnd,
                firstDeclaration < 0 ? 0 : firstDeclaration);
        }

        private static @Nullable SyntaxNode firstIdentifier(SyntaxNode node) {
            for (SyntaxNode child : node.children()) {
                if (child.kind().id().equals(IDENTIFIER))
                    return child;
                if (!child.kind().id().equals("JAVA_TOKEN_WHITESPACE") && !child.kind().id().endsWith("_COMMENT"))
                    return null;
            }
            return null;
        }

        private static boolean qualifier(List<SyntaxToken> tokens, int index) {
            boolean qualifies = significant(tokens, index, 1)
                .map(next -> next.text().equals(".") || next.text().equals("::")).orElse(false);
            boolean selected = significant(tokens, index, -1)
                .map(previous -> previous.text().equals(".") || previous.text().equals("::")).orElse(false);
            return qualifies && !selected;
        }

        private static Optional<SyntaxToken> significant(List<SyntaxToken> tokens, int index, int step) {
            for (int next = index + step; next >= 0 && next < tokens.size(); next += step) {
                String kind = tokens.get(next).kind().id();
                if (!kind.equals("JAVA_TOKEN_WHITESPACE") && !kind.endsWith("_COMMENT"))
                    return Optional.of(tokens.get(next));
            }
            return Optional.empty();
        }

        private static String packageName(SyntaxNode declaration) {
            var name = new StringBuilder();
            Deque<SyntaxNode> pending = new ArrayDeque<>(declaration.children());
            while (!pending.isEmpty()) {
                SyntaxNode node = pending.removeFirst();
                if (node instanceof SyntaxToken token) {
                    if (token.kind().id().equals(IDENTIFIER) || token.kind().id().equals("JAVA_TOKEN_DOT")) {
                        name.append(token.text());
                    }
                } else if (!node.kind().id().equals(ANNOTATION)) {
                    node.children().reversed().forEach(pending::addFirst);
                }
            }
            return name.toString();
        }
    }
}
