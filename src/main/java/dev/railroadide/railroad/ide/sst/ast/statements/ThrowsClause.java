package dev.railroadide.railroad.ide.sst.ast.statements;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.typeref.ClassOrInterfaceTypeRef;
import dev.railroadide.railroad.ide.sst.ast.typeref.TypeVariableRef;
import dev.railroadide.railroad.utility.Either;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record ThrowsClause(
        Span span,
        List<ExceptionType> exceptions) implements AstNode {
    @Override
    public AstKind kind() {
        return AstKind.THROWS_CLAUSE;
    }

    @Override
    public List<AstNode> children() {
        return List.copyOf(exceptions);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitThrowsClause(this);
    }

    public record ExceptionType(
            Span span,
            Either<ClassOrInterfaceTypeRef, TypeVariableRef> type
    ) implements AstNode {
        @Override
        public AstKind kind() {
            return AstKind.EXCEPTION_TYPE;
        }

        @Override
        public List<AstNode> children() {
            return type.map(List::of, List::of);
        }

        @Override
        public <R> R accept(@NotNull AstVisitor<R> visitor) {
            return visitor.visitExceptionType(this);
        }
    }
}
