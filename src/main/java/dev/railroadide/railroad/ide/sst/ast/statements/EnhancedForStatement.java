package dev.railroadide.railroad.ide.sst.ast.statements;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.expression.Expression;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public record EnhancedForStatement(
        Span span,
        LocalVariableDeclarationStatement localVariableDeclaration, // Cannot have an initializer
        Expression iterationExpression,
        Statement body) implements ForStatement {
    @Override
    public AstKind kind() {
        return AstKind.ENHANCED_FOR_STATEMENT;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        children.add(localVariableDeclaration);
        children.add(iterationExpression);
        children.add(body);
        return List.copyOf(children);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitEnhancedForStatement(this);
    }
}
