package dev.railroadide.railroad.ide.sst.ast.program;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record ImportDeclaration(
        Span span,
        String name,
        boolean isStatic,
        boolean isWildcard
) implements AstNode {

    @Override
    public AstKind kind() {
        return AstKind.IMPORT_DECLARATION;
    }

    @Override
    public List<AstNode> children() {
        return List.of(); // Import declarations do not have children nodes.
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitImportDeclaration(this);
    }
}
