package dev.railroadide.railroad.ide.sst.ast.expression;

import dev.railroadide.railroad.ide.sst.ast.AstNode;

public sealed interface Expression extends AstNode permits AssignmentExpression, ConditionalExpression,
        LambdaExpression, MethodInvocationExpression, MethodReferenceExpression, ObjectCreationExpression,
        ArrayCreationExpression, ArrayAccessExpression, FieldAccessExpression, ThisExpression, SuperExpression,
        TypeCastExpression, InstanceofExpression, BinaryExpression, UnaryExpression,
        SwitchExpression {}
