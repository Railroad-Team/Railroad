package dev.railroadide.railroad.ide.sst.ast.expression;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.clazz.ClassDeclaration;
import dev.railroadide.railroad.ide.sst.ast.typeref.ClassOrInterfaceTypeRef;
import dev.railroadide.railroad.ide.sst.ast.typeref.TypeRef;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record ObjectCreationExpression(
        Span span,
        List<TypeRef> typeArguments,
        ClassOrInterfaceTypeRef type,
        List<Expression> arguments,
        Optional<ClassDeclaration> anonymousClassDeclaration
) implements Expression {
    @Override
    public AstKind kind() {
        return AstKind.OBJECT_CREATION_EXPRESSION;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        children.addAll(typeArguments);
        children.add(type);
        children.addAll(arguments);
        anonymousClassDeclaration.ifPresent(children::add);
        return List.copyOf(children);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitObjectCreationExpression(this);
    }
}
