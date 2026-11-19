package dev.railroadide.railroad.ide.sst.semantic.api;

import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Lexical scope constructed during declaration and resolution, then frozen for publication.
 * <p>
 * Construction is thread-confined. {@link SemanticModel.Builder#build()} freezes the
 * entire connected hierarchy before exposing it. Freezing is permanent: retained
 * construction references cannot change a published scope. Frozen scopes support
 * concurrent reads after safe publication without external synchronisation.
 */
public final class Scope {
    private final @Nullable Scope parent;
    private final Map<String, List<Symbol>> declarationsByName = new LinkedHashMap<>();
    private final List<Scope> children = new ArrayList<>();
    private boolean frozen;

    /**
     * Creates a construction scope, registering it with its parent when present.
     *
     * @param parent enclosing scope, or {@code null} for a root
     * @throws IllegalStateException if the parent has already been frozen
     */
    public Scope(@Nullable Scope parent) {
        this.parent = parent;
        if (parent != null) {
            parent.requireMutable();
            parent.children.add(this);
        }
    }

    /**
     * Creates a root scope with no parent.
     *
     * @return a new empty scope with no parent
     */
    public static Scope root() {
        return new Scope(null);
    }

    /**
     * Creates a child scope linked to this scope.
     *
     * @return a new empty scope whose parent is this scope
     * @throws IllegalStateException if this scope has been frozen
     */
    public Scope child() {
        return new Scope(this);
    }

    /**
     * Returns the parent scope when one exists.
     *
     * @return the enclosing scope, or an empty optional for a root
     */
    public Optional<Scope> parent() {
        return Optional.ofNullable(parent);
    }

    /**
     * Returns an immutable list of directly nested scopes in construction order.
     * During construction this is a detached view of the current children.
     *
     * @return child scopes
     */
    public List<Scope> children() {
        return List.copyOf(children);
    }

    /**
     * Permanently freezes this scope, its ancestors, and all their descendants.
     * This operation is idempotent and must run on the construction thread before
     * sharing the hierarchy. Symbols must satisfy {@link Symbol}'s immutability contract.
     *
     * @return this scope
     */
    public Scope freeze() {
        Scope root = this;
        while (root.parent != null) {
            root = root.parent;
        }
        if (root.frozen)
            return this;

        Deque<Scope> pending = new ArrayDeque<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            Scope scope = pending.removeFirst();
            scope.declarationsByName.replaceAll((_, symbols) -> List.copyOf(symbols));
            scope.frozen = true;
            pending.addAll(scope.children);
        }
        return this;
    }

    private void requireMutable() {
        if (frozen)
            throw new IllegalStateException("Scope has been frozen for publication");
    }

    /**
     * Records a symbol declaration in this scope.
     *
     * @param symbol the symbol to append under its simple name
     * @throws IllegalStateException if this scope has been frozen
     */
    public void declare(Symbol symbol) {
        requireMutable();
        Objects.requireNonNull(symbol, "symbol");
        declarationsByName
            .computeIfAbsent(symbol.simpleName(), _ -> new ArrayList<>())
            .add(symbol);
    }

    /**
     * Returns declarations with the given name defined directly in this scope.
     *
     * @param name the simple symbol name to look up
     * @return an immutable list of matching local declarations, or an empty list
     */
    public List<Symbol> lookupLocal(String name) {
        Objects.requireNonNull(name, "name");
        List<Symbol> symbols = declarationsByName.get(name);
        if (symbols == null)
            return List.of();
        return List.copyOf(symbols);
    }

    /**
     * Returns symbols from the first scope that defines the requested name.
     *
     * @param name the simple symbol name to look up
     * @return the matching declarations from the nearest scope that defines the name, or an empty list
     */
    public List<Symbol> lookupNearest(String name) {
        Objects.requireNonNull(name, "name");
        for (Scope current = this; current != null; current = current.parent) {
            List<Symbol> local = current.lookupLocal(name);
            if (!local.isEmpty())
                return local;
        }
        return List.of();
    }

    /**
     * Returns all visible symbols by walking local-to-parent scope chain.
     *
     * @param name the simple symbol name to look up
     * @return an immutable list of matching declarations ordered from this scope to its ancestors
     */
    public List<Symbol> lookupAll(String name) {
        Objects.requireNonNull(name, "name");
        List<Symbol> result = new ArrayList<>();
        for (Scope current = this; current != null; current = current.parent) {
            result.addAll(current.lookupLocal(name));
        }
        return List.copyOf(result);
    }

    /**
     * Returns an immutable snapshot of declarations in this scope only.
     *
     * @return an immutable map from local simple names to immutable declaration lists
     */
    public Map<String, List<Symbol>> snapshotDeclarations() {
        Map<String, List<Symbol>> copy = new LinkedHashMap<>();
        declarationsByName.forEach((name, symbols) -> copy.put(name, List.copyOf(symbols)));
        return Collections.unmodifiableMap(copy);
    }
}
