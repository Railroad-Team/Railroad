package dev.railroadide.railroad.ide.sst.semantic.api;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Mutable lexical scope used during declaration and resolution passes.
 * <p>
 * This type is part of the public semantic model so consumers can inspect the final scope
 * tree, but it is primarily produced by semantic analysis rather than constructed by
 * plugin code.
 */
public final class Scope {
    private final @Nullable Scope parent;
    private final Map<String, List<Symbol>> declarationsByName = new LinkedHashMap<>();

    public Scope(@Nullable Scope parent) {
        this.parent = parent;
    }

    /**
     * Creates a root scope with no parent.
     */
    public static Scope root() {
        return new Scope(null);
    }

    /**
     * Creates a child scope linked to this scope.
     */
    public Scope child() {
        return new Scope(this);
    }

    /**
     * Returns the parent scope when one exists.
     */
    public Optional<Scope> parent() {
        return Optional.ofNullable(parent);
    }

    /**
     * Records a symbol declaration in this scope.
     */
    public void declare(Symbol symbol) {
        Objects.requireNonNull(symbol, "symbol");
        declarationsByName
                .computeIfAbsent(symbol.simpleName(), ignored -> new ArrayList<>())
                .add(symbol);
    }

    /**
     * Returns declarations with the given name defined directly in this scope.
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
     */
    public List<Symbol> lookupAll(String name) {
        Objects.requireNonNull(name, "name");
        List<Symbol> result = new ArrayList<>();
        for (Scope current = this; current != null; current = current.parent)
            result.addAll(current.lookupLocal(name));
        return List.copyOf(result);
    }

    /**
     * Returns an immutable snapshot of declarations in this scope only.
     */
    public Map<String, List<Symbol>> snapshotDeclarations() {
        Map<String, List<Symbol>> copy = new LinkedHashMap<>();
        declarationsByName.forEach((name, symbols) -> copy.put(name, List.copyOf(symbols)));
        return Map.copyOf(copy);
    }
}
