package dev.railroadide.railroad.ide.sst.ast.statements;

public sealed interface ForStatement extends Statement permits BasicForStatement, EnhancedForStatement {
}
