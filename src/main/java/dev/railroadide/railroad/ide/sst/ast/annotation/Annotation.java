package dev.railroadide.railroad.ide.sst.ast.annotation;

import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.generic.Name;

public interface Annotation extends AstNode {
    Name name();
}
