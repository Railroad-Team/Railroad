package dev.railroadide.railroad.ide.sst.semantic.api;

import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxTree;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable semantic analysis output for a syntax tree.
 * <p>
 * This is the bridge between the concrete syntax tree and higher-level inspection logic.
 * It stores:
 * <ul>
 *     <li>the parsed {@linkplain #syntaxTree() syntax tree}</li>
 *     <li>the {@linkplain #rootScope() root lexical scope}</li>
 *     <li>declared symbols for syntax nodes</li>
 *     <li>resolved symbols for references</li>
 *     <li>inferred types for expressions and other typed constructs</li>
 *     <li>semantic diagnostics produced during analysis</li>
 * </ul>
 * <p>
 * Inspection authors usually access these capabilities through
 * {@link dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext}, but the raw model
 * remains useful for tests and lower-level tooling.
 */
public final class SemanticModel {
    private final SyntaxTree syntaxTree;
    private final Scope rootScope;
    private final Map<SyntaxNode, Symbol> declaredSymbols;
    private final Map<SyntaxNode, Symbol> resolvedSymbols;
    private final Map<SyntaxNode, Type> inferredTypes;
    private final List<SemanticDiagnostic> diagnostics;

    private SemanticModel(
            SyntaxTree syntaxTree,
            Scope rootScope,
            Map<SyntaxNode, Symbol> declaredSymbols,
            Map<SyntaxNode, Symbol> resolvedSymbols,
            Map<SyntaxNode, Type> inferredTypes,
            List<SemanticDiagnostic> diagnostics
    ) {
        this.syntaxTree = Objects.requireNonNull(syntaxTree, "syntaxTree");
        this.rootScope = Objects.requireNonNull(rootScope, "rootScope");
        this.declaredSymbols = copyIdentityMap(declaredSymbols, "declaredSymbols");
        this.resolvedSymbols = copyIdentityMap(resolvedSymbols, "resolvedSymbols");
        this.inferredTypes = copyIdentityMap(inferredTypes, "inferredTypes");
        this.diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }

    /**
     * Returns the analysed syntax tree.
     *
     * @return the analysed syntax tree
     */
    public SyntaxTree syntaxTree() {
        return syntaxTree;
    }

    /**
     * Returns the root lexical scope for the file.
     *
     * @return the root scope
     */
    public Scope rootScope() {
        return rootScope;
    }

    /**
     * Returns the symbol declared by the supplied syntax node when one exists.
     *
     * @param node declaration-like syntax node
     * @return the declared symbol, if one is attached to {@code node}
     * @throws NullPointerException if {@code node} is {@code null}
     */
    public Optional<Symbol> declaredSymbol(SyntaxNode node) {
        return Optional.ofNullable(declaredSymbols.get(Objects.requireNonNull(node, "node")));
    }

    /**
     * Returns the symbol resolved for the supplied reference-like node when one exists.
     *
     * @param node reference-like syntax node
     * @return the resolved symbol, if one is attached to {@code node}
     * @throws NullPointerException if {@code node} is {@code null}
     */
    public Optional<Symbol> resolvedSymbol(SyntaxNode node) {
        return Optional.ofNullable(resolvedSymbols.get(Objects.requireNonNull(node, "node")));
    }

    /**
     * Returns the inferred semantic type for the supplied node when one exists.
     *
     * @param node typed syntax node
     * @return the inferred type, if one is attached to {@code node}
     * @throws NullPointerException if {@code node} is {@code null}
     */
    public Optional<Type> inferredType(SyntaxNode node) {
        return Optional.ofNullable(inferredTypes.get(Objects.requireNonNull(node, "node")));
    }

    /**
     * Returns semantic diagnostics produced during analysis.
     *
     * @return immutable semantic diagnostics for the file
     */
    public List<SemanticDiagnostic> diagnostics() {
        return diagnostics;
    }

    /**
     * Returns a new model that appends additional diagnostics to this one.
     *
     * @param additionalDiagnostics diagnostics to append
     * @return this model if {@code additionalDiagnostics} is empty; otherwise a new model
     * @throws NullPointerException if {@code additionalDiagnostics} is {@code null}
     */
    public SemanticModel withAdditionalDiagnostics(List<SemanticDiagnostic> additionalDiagnostics) {
        Objects.requireNonNull(additionalDiagnostics, "additionalDiagnostics");
        if (additionalDiagnostics.isEmpty())
            return this;

        List<SemanticDiagnostic> merged = new java.util.ArrayList<>(diagnostics.size() + additionalDiagnostics.size());
        merged.addAll(diagnostics);
        merged.addAll(additionalDiagnostics);
        return new SemanticModel(syntaxTree, rootScope, declaredSymbols, resolvedSymbols, inferredTypes, merged);
    }

    /**
     * Creates a builder for a new semantic model tied to the supplied syntax tree.
     *
     * @param syntaxTree analysed syntax tree
     * @param rootScope root lexical scope
     * @return a semantic model builder
     * @throws NullPointerException if any argument is {@code null}
     */
    public static Builder builder(SyntaxTree syntaxTree, Scope rootScope) {
        return new Builder(syntaxTree, rootScope);
    }

    private static <T> Map<SyntaxNode, T> copyIdentityMap(Map<SyntaxNode, T> source, String name) {
        source = Objects.requireNonNull(source, name);
        Map<SyntaxNode, T> copy = new IdentityHashMap<>(source.size());
        copy.putAll(source);
        return Map.copyOf(copy);
    }

    /**
     * Incremental builder used by semantic analysis passes.
     */
    public static final class Builder {
        private final SyntaxTree syntaxTree;
        private final Scope rootScope;
        private final Map<SyntaxNode, Symbol> declaredSymbols = new IdentityHashMap<>();
        private final Map<SyntaxNode, Symbol> resolvedSymbols = new IdentityHashMap<>();
        private final Map<SyntaxNode, Type> inferredTypes = new IdentityHashMap<>();
        private final List<SemanticDiagnostic> diagnostics = new java.util.ArrayList<>();

        private Builder(SyntaxTree syntaxTree, Scope rootScope) {
            this.syntaxTree = Objects.requireNonNull(syntaxTree, "syntaxTree");
            this.rootScope = Objects.requireNonNull(rootScope, "rootScope");
        }

        /**
         * Records a declaration produced by the supplied syntax node.
         *
         * @param node declaration site
         * @param symbol declared symbol
         * @return this builder
         * @throws NullPointerException if any argument is {@code null}
         */
        public Builder declare(SyntaxNode node, Symbol symbol) {
            declaredSymbols.put(Objects.requireNonNull(node, "node"), Objects.requireNonNull(symbol, "symbol"));
            return this;
        }

        /**
         * Records the symbol resolved for the supplied syntax node.
         *
         * @param node reference site
         * @param symbol resolved symbol
         * @return this builder
         * @throws NullPointerException if any argument is {@code null}
         */
        public Builder resolve(SyntaxNode node, Symbol symbol) {
            resolvedSymbols.put(Objects.requireNonNull(node, "node"), Objects.requireNonNull(symbol, "symbol"));
            return this;
        }

        /**
         * Records the inferred type for the supplied syntax node.
         *
         * @param node typed syntax node
         * @param type inferred type
         * @return this builder
         * @throws NullPointerException if any argument is {@code null}
         */
        public Builder type(SyntaxNode node, Type type) {
            inferredTypes.put(Objects.requireNonNull(node, "node"), Objects.requireNonNull(type, "type"));
            return this;
        }

        /**
         * Appends a semantic diagnostic.
         *
         * @param diagnostic diagnostic to add
         * @return this builder
         * @throws NullPointerException if {@code diagnostic} is {@code null}
         */
        public Builder diagnostic(SemanticDiagnostic diagnostic) {
            diagnostics.add(Objects.requireNonNull(diagnostic, "diagnostic"));
            return this;
        }

        /**
         * Builds an immutable semantic model snapshot.
         *
         * @return immutable semantic model
         */
        public SemanticModel build() {
            return new SemanticModel(syntaxTree, rootScope, declaredSymbols, resolvedSymbols, inferredTypes, diagnostics);
        }
    }
}
