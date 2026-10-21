package dev.railroadide.railroad.ide.sst.ast.generic;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.annotation.Annotation;
import dev.railroadide.railroad.ide.sst.ast.expression.NameExpression;
import dev.railroadide.railroad.ide.sst.ast.typeref.TypeRef;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A pattern used for Java pattern matching.
 */
public sealed interface Pattern extends AstNode
    permits Pattern.MatchAllPattern, Pattern.RecordPattern, Pattern.TypeTestPattern {
    /**
     * A pattern testing a type and optionally binding the matched value to a variable.
     *
     * @param span source range occupied by this node
     * @param annotations annotations attached to this node
     * @param modifiers modifiers attached to the declaration
     * @param type type tested by the pattern
     * @param variable optional name receiving the matched value
     */
    record TypeTestPattern(
        Span span,
        List<Annotation> annotations,
        List<Modifier> modifiers,
        TypeRef type,
        Optional<NameExpression> variable
    ) implements Pattern {
        @Override
        public AstKind kind() {
            return AstKind.TYPE_TEST_PATTERN;
        }

        @Override
        public List<AstNode> children() {
            List<AstNode> children = new ArrayList<>();
            children.addAll(annotations);
            children.addAll(modifiers);
            children.add(type);
            variable.ifPresent(children::add);
            return List.copyOf(children);
        }

        @Override
        public <R> R accept(@NotNull AstVisitor<R> visitor) {
            return visitor.visitTypeTestPattern(this);
        }
    }

    /**
     * A pattern deconstructing a record into component patterns.
     *
     * @param span source range occupied by this node
     * @param type record type to deconstruct
     * @param components patterns applied to record components in declaration order
     */
    record RecordPattern(Span span, TypeRef type, List<Pattern> components) implements Pattern {
        @Override
        public AstKind kind() {
            return AstKind.RECORD_PATTERN;
        }

        @Override
        public List<AstNode> children() {
            List<AstNode> children = new ArrayList<>();
            children.add(type);
            children.addAll(components);
            return List.copyOf(children);
        }

        @Override
        public <R> R accept(@NotNull AstVisitor<R> visitor) {
            return visitor.visitRecordPattern(this);
        }
    }

    /**
     * A pattern accepting any matched value without binding a variable.
     *
     * @param span source range occupied by this node
     */
    record MatchAllPattern(Span span) implements Pattern {
        @Override
        public AstKind kind() {
            return AstKind.MATCH_ALL_PATTERN;
        }

        @Override
        public List<AstNode> children() {
            return List.of();
        }

        @Override
        public <R> R accept(@NotNull AstVisitor<R> visitor) {
            return visitor.visitMatchAllPattern(this);
        }
    }
}
