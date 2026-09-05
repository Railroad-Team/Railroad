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
 * A module directive exporting a package, optionally to selected modules.
 *
 * @param span source range occupied by this node
 * @param packageName package targeted by the directive
 * @param moduleNames target modules, or an empty list for an unqualified export
 */
public record ExportsDirective(
    Span span,
    NameExpression packageName,
    List<NameExpression> moduleNames
) implements ModuleDirective {
    @Override
    public AstKind kind() {
        return AstKind.EXPORTS_DIRECTIVE;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        children.add(packageName);
        children.addAll(moduleNames);
        return List.copyOf(children);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitExportsDirective(this);
    }
}
