package dev.railroadide.railroad.ide.sst.ast.expression;

import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.annotation.ElementValue;

public sealed interface Expression extends AstNode, ElementValue permits AssignmentExpression, ConditionalExpression,
        LambdaExpression, MethodInvocationExpression, MethodReferenceExpression, ObjectCreationExpression,
        ArrayCreationExpression, ArrayAccessExpression, FieldAccessExpression, ThisExpression, SuperExpression,
        TypeCastExpression, InstanceofExpression, BinaryExpression, UnaryExpression, SwitchExpression, NameExpression {
}
