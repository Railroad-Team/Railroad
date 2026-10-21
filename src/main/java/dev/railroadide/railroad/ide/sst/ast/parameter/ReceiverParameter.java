package dev.railroadide.railroad.ide.sst.ast.parameter;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.annotation.Annotation;
import dev.railroadide.railroad.ide.sst.ast.typeref.TypeRef;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * An explicit receiver parameter carrying annotations and its declared receiver type.
 *
 * @param span source range occupied by this node
 * @param annotations annotations attached to this node
 * @param type declared type of the receiver
 * @param receiverType receiver keyword represented by the parameter
 */
public record ReceiverParameter(
    Span span,
    List<Annotation> annotations,
    TypeRef type,
    ReceiverType receiverType
) implements AstNode {
    @Override
    public AstKind kind() {
        return AstKind.RECEIVER_PARAMETER;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        children.addAll(annotations);
        children.add(type);
        return List.copyOf(children);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitReceiverParameter(this);
    }

    /**
     * Identifies the receiver keyword represented in the AST.
     */
    public enum ReceiverType {
        /**
         * The this receiver keyword.
         */
        THIS,
        /**
         * The super receiver keyword.
         */
        SUPER
    }
}
