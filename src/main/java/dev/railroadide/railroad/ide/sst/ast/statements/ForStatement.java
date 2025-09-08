package dev.railroadide.railroad.ide.sst.ast.statements;

import dev.railroadide.railroad.ide.sst.ast.AstNode;

public sealed interface ForStatement extends AstNode permits BasicForStatement, EnhancedForStatement {
}
