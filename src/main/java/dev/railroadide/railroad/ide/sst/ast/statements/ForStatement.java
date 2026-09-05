package dev.railroadide.railroad.ide.sst.ast.statements;

/**
 * A basic or enhanced for-loop statement.
 */
public sealed interface ForStatement extends Statement permits BasicForStatement, EnhancedForStatement {
}
