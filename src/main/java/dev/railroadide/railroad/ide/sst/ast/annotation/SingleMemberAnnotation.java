package dev.railroadide.railroad.ide.sst.ast.annotation;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.expression.NameExpression;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record SingleMemberAnnotation(
        Span span,
        NameExpression name,
        ElementValue value
) implements Annotation {
    @Override
    public AstKind kind() {
        return AstKind.SINGLE_MEMBER_ANNOTATION;
    }

    @Override
    public List<AstNode> children() {
        return List.of(name, value);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitSingleMemberAnnotation(this);
    }
}
