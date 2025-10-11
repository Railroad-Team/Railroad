package dev.railroadide.railroad.ide.sst.ast.expression;

public sealed interface LiteralExpression extends Expression permits IntegerLiteralExpression,
        FloatingPointLiteralExpression, BooleanLiteralExpression, CharacterLiteralExpression,
        StringLiteralExpression, NullLiteralExpression, ClassLiteralExpression {
}
