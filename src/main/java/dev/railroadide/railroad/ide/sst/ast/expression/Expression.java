package dev.railroadide.railroad.ide.sst.ast.expression;

import dev.railroadide.railroad.ide.sst.ast.AstNode;
import org.codehaus.groovy.ast.expr.MethodReferenceExpression;
import org.eclipse.jdt.core.dom.ThisExpression;

public sealed interface Expression extends AstNode permits AssignmentExpression, ConditionalExpression,
        LambdaExpression, MethodInvocationExpression, MethodReferenceExpression, ObjectCreationExpression,
        ArrayCreationExpression, ArrayAccessExpression, FieldAccessExpression, ThisExpression, SuperExpression,
        TypeCastExpression, InstanceofExpression, BinaryExpression, UnaryExpression,
        SwitchExpression, YieldExpression {}
