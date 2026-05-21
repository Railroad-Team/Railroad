package dev.railroadide.railroad.ide.sst.semantic.api;

import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Basic immutable symbol implementation used by semantic passes.
 */
public final class SimpleSymbol implements Symbol {
    private final SymbolKind kind;
    private final String simpleName;
    private final @Nullable String qualifiedName;
    private final @Nullable SyntaxNode declaration;

    public SimpleSymbol(SymbolKind kind, String simpleName, @Nullable String qualifiedName, @Nullable SyntaxNode declaration) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.simpleName = Objects.requireNonNull(simpleName, "simpleName");
        if (simpleName.isBlank())
            throw new IllegalArgumentException("simpleName cannot be blank");
        this.qualifiedName = qualifiedName;
        this.declaration = declaration;
    }

    @Override
    public SymbolKind kind() {
        return kind;
    }

    @Override
    public String simpleName() {
        return simpleName;
    }

    @Override
    public Optional<String> qualifiedName() {
        return Optional.ofNullable(qualifiedName);
    }

    @Override
    public Optional<SyntaxNode> declaration() {
        return Optional.ofNullable(declaration);
    }
}
