package dev.railroadide.railroad.ide.sst.ast.statements.switches;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A case or default label selecting a switch rule.
 */
public sealed interface SwitchLabel extends AstNode permits SwitchLabel.CaseLabel, SwitchLabel.DefaultLabel {
    /**
     * A switch case label containing constant, null, or pattern alternatives.
     *
     * @param span source range occupied by this node
     * @param items case alternatives
     */
    record CaseLabel(Span span, List<CaseItem> items) implements SwitchLabel {
        @Override
        public AstKind kind() {
            return AstKind.CASE_LABEL;
        }

        @Override
        public List<AstNode> children() {
            return List.copyOf(items);
        }

        @Override
        public <R> R accept(@NotNull AstVisitor<R> visitor) {
            return visitor.visitCaseLabel(this);
        }
    }

    /**
     * The default label of a switch rule.
     *
     * @param span source range occupied by this node
     */
    record DefaultLabel(Span span) implements SwitchLabel {
        @Override
        public AstKind kind() {
            return AstKind.DEFAULT_LABEL;
        }

        @Override
        public List<AstNode> children() {
            return List.of();
        }

        @Override
        public <R> R accept(@NotNull AstVisitor<R> visitor) {
            return visitor.visitDefaultLabel(this);
        }
    }
}
