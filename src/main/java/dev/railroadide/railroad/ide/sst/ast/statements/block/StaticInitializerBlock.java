package dev.railroadide.railroad.ide.sst.ast.statements.block;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.generic.ClassMember;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record StaticInitializerBlock(Span span, BlockStatement body) implements ClassMember {
    @Override
    public AstKind kind() {
        return AstKind.STATIC_INITIALIZER_BLOCK;
    }

    @Override
    public List<AstNode> children() {
        return List.of(body);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitStaticInitializerBlock(this);
    }
}
