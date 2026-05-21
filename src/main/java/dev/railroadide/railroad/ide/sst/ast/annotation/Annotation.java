package dev.railroadide.railroad.ide.sst.ast.annotation;

import dev.railroadide.railroad.ide.sst.ast.expression.NameExpression;

public interface Annotation extends ElementValue {
    NameExpression name();
}
