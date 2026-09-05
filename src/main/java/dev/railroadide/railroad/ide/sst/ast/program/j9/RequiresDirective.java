package dev.railroadide.railroad.ide.sst.ast.program.j9;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.expression.NameExpression;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A module dependency with optional static and transitive modifiers.
 *
 * @param span source range occupied by this node
 * @param isStatic whether the static modifier is present
 * @param isTransitive whether dependent modules also read the required module
 * @param moduleName required module name
 */
public record RequiresDirective(
    Span span,
    boolean isStatic,
    boolean isTransitive,
    NameExpression moduleName
) implements ModuleDirective {
    @Override
    public AstKind kind() {
        return AstKind.REQUIRES_DIRECTIVE;
    }

    @Override
    public List<AstNode> children() {
        return List.of(moduleName);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitRequiresDirective(this);
    }
}
