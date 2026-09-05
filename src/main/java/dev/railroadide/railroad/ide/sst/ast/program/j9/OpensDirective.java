package dev.railroadide.railroad.ide.sst.ast.program.j9;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.expression.NameExpression;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A module directive opening a package for reflective access, optionally to selected modules.
 *
 * @param span source range occupied by this node
 * @param packageName package targeted by the directive
 * @param targetModules target modules, or an empty list for an unqualified opens directive
 */
public record OpensDirective(
    Span span,
    NameExpression packageName,
    List<NameExpression> targetModules
) implements ModuleDirective {
    @Override
    public AstKind kind() {
        return AstKind.OPENS_DIRECTIVE;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        children.add(packageName);
        children.addAll(targetModules);
        return List.copyOf(children);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitOpensDirective(this);
    }
}
