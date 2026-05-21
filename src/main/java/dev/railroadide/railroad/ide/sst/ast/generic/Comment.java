package dev.railroadide.railroad.ide.sst.ast.generic;

import dev.railroadide.railroad.ide.sst.ast.AstNode;

public sealed interface Comment extends AstNode permits LineComment, BlockComment, JavadocComment {
}
