package dev.railroadide.railroad.ide.sst.ast.literal;

import dev.railroadide.railroad.ide.sst.ast.AstNode;

public sealed interface Literal extends AstNode permits IntegerLiteral, FloatingPointLiteral, BooleanLiteral, CharacterLiteral, StringLiteral, NullLiteral {
}
