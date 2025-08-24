package dev.railroadide.railroad.ide.sst.ast.program.j9;

import dev.railroadide.railroad.ide.sst.ast.AstNode;

public sealed interface ModuleDirective extends AstNode permits
        RequiresDirective,
        ExportsDirective,
        OpensDirective,
        UsesDirective,
        ProvidesDirective {
}
