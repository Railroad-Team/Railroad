package dev.railroadide.railroad.ide.sst.ast.generic;

import dev.railroadide.railroad.ide.sst.ast.AstNode;

/**
 * A source comment retained in the AST.
 */
public sealed interface Comment extends AstNode permits LineComment, BlockComment, JavadocComment {
}
