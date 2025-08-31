package dev.railroadide.railroad.ide.sst.ast.expression;

import dev.railroadide.railroad.ide.sst.ast.annotation.ElementValue;

// TODO: Reconsider if Expression should extend ElementValue directly
public sealed interface Expression extends ElementValue permits AssignmentExpression, ConditionalExpression,
        LambdaExpression, MethodInvocationExpression, MethodReferenceExpression, ObjectCreationExpression,
        ArrayCreationExpression, ArrayAccessExpression, FieldAccessExpression, ThisExpression, SuperExpression,
        TypeCastExpression, InstanceofExpression, BinaryExpression, UnaryExpression,
        SwitchExpression {}
