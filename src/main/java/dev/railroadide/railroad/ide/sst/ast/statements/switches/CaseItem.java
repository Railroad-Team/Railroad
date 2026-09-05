package dev.railroadide.railroad.ide.sst.ast.statements.switches;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.expression.Expression;
import dev.railroadide.railroad.ide.sst.ast.generic.Pattern;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * An individual constant, null alternative, or pattern in a switch case.
 */
public sealed interface CaseItem extends AstNode
    permits CaseItem.CaseConstant, CaseItem.CasePattern, CaseItem.CaseNull {
    /**
     * A constant-expression alternative within a switch case label.
     *
     * @param span source range occupied by this node
     * @param expression constant expression compared with the switch selector
     */
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
        public <R> R accept(@NotNull AstVisitor<R> visitor) {
            return visitor.visitCaseConstant(this);
        }
    }

    /**
     * A pattern alternative within a switch case label, with an associated guard.
     *
     * @param span source range occupied by this node
     * @param pattern pattern to match
     * @param guard guard associated with the pattern
     */
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
        public <R> R accept(@NotNull AstVisitor<R> visitor) {
            return visitor.visitCasePattern(this);
        }

        /**
         * A boolean guard restricting a switch pattern match.
         *
         * @param span source range occupied by this node
         * @param expression boolean condition required for the pattern to match
         */
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
            public <R> R accept(@NotNull AstVisitor<R> visitor) {
                return visitor.visitCasePatternGuard(this);
            }
        }
    }

    /**
     * The {@code null} alternative within a switch case label.
     *
     * @param span source range occupied by this node
     */
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
        public <R> R accept(@NotNull AstVisitor<R> visitor) {
            return visitor.visitCaseNull(this);
        }
    }
}
