package dev.railroadide.railroad.ide.sst.ast.statements;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.statements.block.BlockStatement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A protected block with optional resources, exception handlers, and a finally clause.
 *
 * @param span source range occupied by this node
 * @param resources resource declarations managed by the try statement
 * @param tryBlock protected block
 * @param catchClauses exception handlers in source order
 * @param finallyBlock optional cleanup block
 */
public record TryStatement(
    Span span,
    List<LocalVariableDeclarationStatement> resources,
    BlockStatement tryBlock,
    List<CatchClause> catchClauses,
    Optional<FinallyClause> finallyBlock
) implements Statement {
    @Override
    public AstKind kind() {
        return AstKind.TRY_STATEMENT;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        children.addAll(resources);
        children.add(tryBlock);
        children.addAll(catchClauses);
        finallyBlock.ifPresent(children::add);
        return List.copyOf(children);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitTryStatement(this);
    }
}
