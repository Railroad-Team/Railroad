package dev.railroadide.railroad.ide.sst.ast.program.j9;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.expression.NameExpression;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A module directive declaring consumption of a service type.
 *
 * @param span source range occupied by this node
 * @param serviceName service type consumed by the module
 */
public record UsesDirective(
    Span span,
    NameExpression serviceName
) implements ModuleDirective {
    @Override
    public AstKind kind() {
        return AstKind.USES_DIRECTIVE;
    }

    @Override
    public List<AstNode> children() {
        return List.of(serviceName);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitUsesDirective(this);
    }
}
