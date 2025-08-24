package dev.railroadide.railroad.ide.sst.ast.statements.switches;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.expression.Expression;
import dev.railroadide.railroad.ide.sst.ast.generic.Pattern;

import java.util.List;

public sealed interface CaseItem extends AstNode permits CaseItem.CaseConstant, CaseItem.CasePattern, CaseItem.CaseNull {
    record CaseConstant(Span span, Expression expression) implements CaseItem {
        @Override
        public AstKind kind() {
            return AstKind.CASE_CONSTANT;
        }

        @Override
        public List<AstNode> children() {
            return List.of(expression);
        }

        @Override
        public <R> R accept(AstVisitor<R> visitor) {
            return visitor.visitCaseConstant(this);
        }
    }

    record CasePattern(Span span, Pattern pattern, Guard guard) implements CaseItem {
        @Override
        public AstKind kind() {
            return AstKind.CASE_PATTERN;
        }

        @Override
        public List<AstNode> children() {
            return List.of(pattern);
        }

        @Override
        public <R> R accept(AstVisitor<R> visitor) {
            return visitor.visitCasePattern(this);
        }

        public record Guard(Span span, Expression expression) implements AstNode {
            @Override
            public AstKind kind() {
                return AstKind.CASE_PATTERN_GUARD;
            }

            @Override
            public List<AstNode> children() {
                return List.of(expression);
            }

            @Override
            public <R> R accept(AstVisitor<R> visitor) {
                return visitor.visitCasePatternGuard(this);
            }
        }
    }

    record CaseNull(Span span) implements CaseItem {
        @Override
        public AstKind kind() {
            return AstKind.CASE_NULL;
        }

        @Override
        public List<AstNode> children() {
            return List.of();
        }

        @Override
        public <R> R accept(AstVisitor<R> visitor) {
            return visitor.visitCaseNull(this);
        }
    }
}
