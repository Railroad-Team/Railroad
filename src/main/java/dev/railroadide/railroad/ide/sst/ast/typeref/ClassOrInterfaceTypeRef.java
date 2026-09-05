package dev.railroadide.railroad.ide.sst.ast.typeref;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.annotation.Annotation;
import dev.railroadide.railroad.ide.sst.ast.expression.NameExpression;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A qualified class or interface type assembled from annotated, parameterized name segments.
 *
 * @param span source range occupied by this node
 * @param parts name segments in source order
 */
public record ClassOrInterfaceTypeRef(Span span, List<Part> parts) implements TypeRef {
    @Override
    public AstKind kind() {
        return AstKind.CLASS_OR_INTERFACE_TYPE;
    }

    @Override
    public List<AstNode> children() {
        return List.copyOf(parts);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitClassOrInterfaceType(this);
    }

    /**
     * One annotated segment of a qualified class or interface type.
     *
     * @param span source range occupied by this node
     * @param annotations annotations attached to this node
     * @param name identifier for this qualified type segment
     * @param typeArguments explicit type arguments
     */
    public record Part(
        Span span,
        List<Annotation> annotations,
        NameExpression name,
        List<TypeRef> typeArguments
    ) implements AstNode {
        @Override
        public AstKind kind() {
            return AstKind.CLASS_OR_INTERFACE_TYPE_PART;
        }

        @Override
        public List<AstNode> children() {
            List<AstNode> children = new ArrayList<>();
            children.addAll(annotations);
            children.add(name);
            children.addAll(typeArguments);
            return List.copyOf(children);
        }

        @Override
        public <R> R accept(@NotNull AstVisitor<R> visitor) {
            return visitor.visitClassOrInterfaceTypePart(this);
        }
    }
}
