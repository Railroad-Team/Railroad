package dev.railroadide.railroad.ide.sst.ast.statements;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.annotation.Annotation;
import dev.railroadide.railroad.ide.sst.ast.expression.Expression;
import dev.railroadide.railroad.ide.sst.ast.generic.Modifier;
import dev.railroadide.railroad.ide.sst.ast.generic.Name;
import dev.railroadide.railroad.ide.sst.ast.statements.block.BlockStatement;
import dev.railroadide.railroad.ide.sst.ast.typeref.TypeRef;
import org.eclipse.jdt.core.dom.CatchClause;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record TryStatement(
        Span span,
        List<Resource> resources,
        BlockStatement tryBlock,
        List<CatchClause> catchClauses,
        Optional<FinallyClause> finallyBlock) implements Statement {
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
        return children;
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitTryStatement(this);
    }

    public record Resource(Span span,
                           List<Annotation> annotations,
                           Optional<Modifier> finalModifier,
                           TypeRef type,
                           boolean isVar,
                           Name name,
                           Expression expression) implements AstNode {
        @Override
        public AstKind kind() {
            return AstKind.TRY_RESOURCE;
        }

        @Override
        public List<AstNode> children() {
            List<AstNode> children = new ArrayList<>();
            children.addAll(annotations);
            finalModifier.ifPresent(children::add);
            children.add(type);
            children.add(name);
            children.add(expression);
            return children;
        }

        @Override
        public <R> R accept(@NotNull AstVisitor<R> visitor) {
            return visitor.visitTryResource(this);
        }
    }
}
