package dev.railroadide.railroad.ide.sst.ast.statements;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.statements.block.BlockStatement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The cleanup block associated with a try statement.
 *
 * @param span source range occupied by this node
 * @param body cleanup block executed when leaving the associated try statement
 */
public record FinallyClause(
    Span span,
    BlockStatement body
) implements AstNode {
    @Override
    public AstKind kind() {
        return AstKind.FINALLY_CLAUSE;
    }

    @Override
    public List<AstNode> children() {
        return List.of(body);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitFinallyClause(this);
    }
}
