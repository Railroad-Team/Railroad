package dev.railroadide.railroad.ide.sst.examples;

import dev.railroadide.railroad.ide.diagnostics.*;
import dev.railroadide.railroad.ide.diagnostics.EditorDiagnostic.TextEditorDiagnostic;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceEdit;
import dev.railroadide.railroad.ide.sst.provider.api.*;
import dev.railroadide.railroad.ide.sst.request.api.*;
import dev.railroadide.railroad.ide.sst.semantic.api.*;
import dev.railroadide.railroad.ide.sst.syntax.api.*;
import dev.railroadide.railroad.plugin.spi.editor.TextReplacement;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Independent sample language: "let name = value" declares a symbol and "use name" refers to it.
 * This provider imports only public neutral contracts and has no editor or Java implementation dependency.
 * Every request is computed from its captured snapshots; no persistent cache can outlive a session.
 */
public final class TinyLanguageContribution implements LanguageContribution {
    /**
     * Namespaced identity of the example text language.
     */
    public static final LanguageId ID = new LanguageId("example:tiny");
    private static final Pattern DECLARATION = Pattern.compile("let\\s+([A-Za-z_][A-Za-z_0-9]*)(.*)");
    private static final Pattern REFERENCE = Pattern.compile("use\\s+([A-Za-z_][A-Za-z_0-9]*)\\s*");
    private static final SymbolKind VARIABLE = () -> "example:tiny.variable";

    @Override
    public LanguageId id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Tiny";
    }

    @Override
    public LanguageMatcher matcher() {
        return LanguageMatcher.extension("tiny");
    }

    @Override
    public Set<CapabilityKey<?>> supportedCapabilities() {
        return Set.of(SstCapabilities.PARSING, SstCapabilities.SEMANTICS, SstCapabilities.INDEX_EXTRACTION,
            SstCapabilities.INDEX_QUERY, SstCapabilities.NAVIGATION, SstCapabilities.CODE_ACTIONS,
            SstCapabilities.DIAGNOSTICS);
    }

    @Override
    public LanguageSession openSession(LanguageSessionContext context) {
        var capabilities = CapabilityRegistry.builder()
            .add(SstCapabilities.PARSING, request -> new FeatureResult<>(request, analyze(request).tree()))
            .add(SstCapabilities.SEMANTICS, request -> new FeatureResult<>(request, analyze(request).semantics()))
            .add(SstCapabilities.INDEX_EXTRACTION,
                request -> new FeatureResult<>(request, analyze(request).symbols()))
            .add(SstCapabilities.INDEX_QUERY, this::query)
            .add(SstCapabilities.NAVIGATION, this::definitions)
            .add(SstCapabilities.CODE_ACTIONS,
                request -> new FeatureResult<>(request, analyze(request).actions()))
            .add(SstCapabilities.DIAGNOSTICS, new DiagnosticsProvider<TextEditorDiagnostic>() {
                @Override
                public List<TextEditorDiagnostic> compute(DocumentSnapshot snapshot) {
                    var workspace = new WorkspaceSnapshot("tiny", Map.of(snapshot.id(), snapshot),
                        new WorkspaceConfigurationSnapshot(0, Set.of()), 0);
                    return analyze(new FeatureRequestContext(workspace, snapshot, CancellationToken.none(),
                        Deadline.none())).diagnostics();
                }

                @Override
                public FeatureResult<List<TextEditorDiagnostic>> compute(FeatureRequestContext request) {
                    return new FeatureResult<>(request, analyze(request).diagnostics());
                }
            }).build();
        return new LanguageSession(capabilities);
    }

    private FeatureResult<List<SymbolOccurrence>> query(WorkspaceRequestContext context, String name) {
        var found = new ArrayList<SymbolOccurrence>();
        for (DocumentSnapshot snapshot : context.workspaceSnapshot().documents().values()) {
            context.checkActive();
            if (!(snapshot instanceof TextDocumentSnapshot) || !snapshot.languageId().equals(ID.value()))
                continue;
            var request = new FeatureRequestContext(context.workspaceSnapshot(), snapshot,
                context.cancellationToken(), context.deadline());
            analyze(request).symbols().stream().filter(symbol -> symbol.symbol().simpleName().equals(name))
                .forEach(found::add);
        }
        context.checkActive();
        return new FeatureResult<>(context, List.copyOf(found));
    }

    private FeatureResult<List<Location<?>>> definitions(FeatureRequestContext context, int offset) {
        Analysis analysis = analyze(context);
        if (offset < 0 || offset > analysis.tree().documentSnapshot().text().length())
            throw new IndexOutOfBoundsException(offset);
        for (SyntaxNode node : analysis.tree().root().children()) {
            if (offset < node.start() || offset >= node.end())
                continue;
            Optional<Symbol> symbol = analysis.semantics().resolvedSymbol(node);
            if (symbol.isEmpty()) {
                symbol = analysis.semantics().declaredSymbol(node);
            }
            if (symbol.isPresent()) {
                Symbol target = symbol.get();
                return new FeatureResult<>(context, analysis.symbols().stream()
                    .filter(entry -> entry.symbol() == target).map(SymbolOccurrence::location).toList());
            }
        }
        return new FeatureResult<>(context, List.of());
    }

    private static Analysis analyze(FeatureRequestContext context) {
        context.checkActive();
        if (!(context.documentSnapshot() instanceof TextDocumentSnapshot snapshot))
            throw new IllegalArgumentException("Tiny requires a text snapshot");
        if (!snapshot.languageId().equals(ID.value()))
            throw new IllegalArgumentException("Not a Tiny document");
        String text = snapshot.text().toString();
        var root = new Root(text, context);
        var tree = new SyntaxTree(snapshot, root);
        Scope scope = Scope.root();
        var semantic = SemanticModel.builder(tree, scope);
        var symbols = new ArrayList<SymbolOccurrence>();
        var diagnostics = new ArrayList<TextEditorDiagnostic>();
        var actions = new ArrayList<CodeAction>();
        var names = new LinkedHashMap<String, Symbol>();
        for (SyntaxNode child : root.children()) {
            context.checkActive();
            Line line = (Line) child;
            String statement = line.text().strip();
            var declaration = DECLARATION.matcher(statement);
            if (!declaration.matches())
                continue;
            String name = declaration.group(1);
            var symbol = new SimpleSymbol(VARIABLE, name, null, line);
            semantic.declare(line, symbol);
            scope.declare(symbol);
            symbols.add(new SymbolOccurrence(symbol, Location.TextLocation.from(snapshot, line.start(), line.end())));
            if (names.putIfAbsent(name, symbol) != null) {
                diagnostics.add(diagnostic(snapshot, line, "duplicate", "Duplicate declaration: " + name,
                    DiagnosticOrigin.SEMANTIC));
            }
            if (!declaration.group(2).stripLeading().startsWith("=")) {
                diagnostics.add(diagnostic(snapshot, line, "missing-equals", "Expected '=' after " + name,
                    DiagnosticOrigin.SYNTAX));
                int leadingWhitespace = line.text().length() - line.text().stripLeading().length();
                int insertion = line.start() + leadingWhitespace + declaration.end(1);
                String title = "Insert '=' after " + name;
                actions.add(new CodeAction(title, WorkspaceEdit.builder(title)
                    .replace(snapshot, new TextReplacement(new TextRange(insertion, insertion), " ="))
                    .build()));
            }
        }
        for (SyntaxNode child : root.children()) {
            context.checkActive();
            Line line = (Line) child;
            String statement = line.text().strip();
            var reference = REFERENCE.matcher(statement);
            if (reference.matches()) {
                String name = reference.group(1);
                Symbol target = names.get(name);
                if (target == null) {
                    diagnostics.add(diagnostic(snapshot, line, "unresolved", "Unknown name: " + name,
                        DiagnosticOrigin.SEMANTIC));
                } else {
                    semantic.resolve(line, target);
                }
            } else if (!statement.isEmpty() && !statement.startsWith("#")
                && !DECLARATION.matcher(statement).matches()) {
                diagnostics.add(diagnostic(snapshot, line, "statement", "Expected 'let' or 'use'",
                    DiagnosticOrigin.SYNTAX));
            }
        }
        context.checkActive();
        return new Analysis(tree, semantic.build(), List.copyOf(symbols), List.copyOf(diagnostics),
            List.copyOf(actions));
    }

    private static TextEditorDiagnostic diagnostic(
        TextDocumentSnapshot snapshot,
        Line line,
        String code,
        String message,
        DiagnosticOrigin origin
    ) {
        return new TextEditorDiagnostic(Location.TextLocation.from(snapshot, line.start(), line.end()),
            DiagnosticSeverity.ERROR, "tiny." + code, message, ID.value(), origin, Set.of(), List.of(), "");
    }

    private record Analysis(
        SyntaxTree tree,
        SemanticModel semantics,
        List<SymbolOccurrence> symbols,
        List<TextEditorDiagnostic> diagnostics,
        List<CodeAction> actions
    ) {
    }

    private static final class Root implements SyntaxNode {
        private final int end;
        private final List<SyntaxNode> children;

        private Root(String text, FeatureRequestContext context) {
            end = text.length();
            var lines = new ArrayList<SyntaxNode>();
            int start = 0;
            for (int offset = 0; offset < text.length(); offset++) {
                if ((offset & 1023) == 0) {
                    context.checkActive();
                }
                if (text.charAt(offset) == '\n') {
                    lines.add(new Line(this, start, text.substring(start, offset + 1)));
                    start = offset + 1;
                }
            }
            if (start < text.length()) {
                lines.add(new Line(this, start, text.substring(start)));
            }
            children = List.copyOf(lines);
        }

        @Override
        public SyntaxKind kind() {
            return SyntaxKind.of("example:tiny.root");
        }

        @Override
        public int start() {
            return 0;
        }

        @Override
        public int end() {
            return end;
        }

        @Override
        public List<SyntaxNode> children() {
            return children;
        }

        @Override
        public Optional<SyntaxNode> parent() {
            return Optional.empty();
        }
    }

    private record Line(Root root, int start, String text) implements SyntaxToken {
        @Override
        public SyntaxKind kind() {
            return SyntaxKind.of("example:tiny.line");
        }

        @Override
        public int end() {
            return start + text.length();
        }

        @Override
        public Optional<SyntaxNode> parent() {
            return Optional.of(root);
        }
    }
}
