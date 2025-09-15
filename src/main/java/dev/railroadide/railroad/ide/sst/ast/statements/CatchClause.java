package dev.railroadide.railroad.ide.sst.ast.statements;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.expression.NameExpression;
import dev.railroadide.railroad.ide.sst.ast.statements.block.BlockStatement;
import dev.railroadide.railroad.ide.sst.ast.typeref.SugarTypeRef;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public record CatchClause(
        Span span,
        List<SugarTypeRef> exceptionTypes,
        NameExpression variableName,
        BlockStatement body
) implements AstNode {
    @Override
    public AstKind kind() {
        return AstKind.CATCH_CLAUSE;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        children.addAll(exceptionTypes);
        children.add(variableName);
        children.add(body);
        return List.copyOf(children);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitCatchClause(this);
    }
}
