package dev.railroadide.railroad.ide.sst.ast.generic;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.typeref.ClassOrInterfaceTypeRef;
import dev.railroadide.railroad.ide.sst.ast.typeref.TypeRef;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public sealed interface Pattern extends AstNode permits Pattern.MatchAllPattern, Pattern.RecordPattern, Pattern.TypeTestPattern {
    record TypeTestPattern(Span span, TypeRef type, Optional<Name> variable) implements Pattern {
        @Override
        public AstKind kind() {
            return AstKind.TYPE_TEST_PATTERN;
        }

        @Override
        public List<AstNode> children() {
            List<AstNode> children = new ArrayList<>();
            children.add(type);
            variable.ifPresent(children::add);
            return List.copyOf(children);
        }

        @Override
        public <R> R accept(@NotNull AstVisitor<R> visitor) {
            return visitor.visitTypeTestPattern(this);
        }
    }

    record RecordPattern(Span span, ClassOrInterfaceTypeRef type, List<Pattern> components,
                         Optional<Name> name) implements Pattern {
        @Override
        public AstKind kind() {
            return AstKind.RECORD_PATTERN;
        }

        @Override
        public List<AstNode> children() {
            List<AstNode> children = new ArrayList<>();
            children.add(type);
            children.addAll(components);
            name.ifPresent(children::add);
            return List.copyOf(children);
        }

        @Override
        public <R> R accept(@NotNull AstVisitor<R> visitor) {
            return visitor.visitRecordPattern(this);
        }
    }

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
