package dev.railroadide.railroad.ide.sst.ast;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface AstNode {
    AstKind kind();
    Span span();
    List<AstNode> children();

    <R> R accept(@NotNull AstVisitor<R> visitor);
}
