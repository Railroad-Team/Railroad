package dev.railroadide.railroad.ide.sst.ast.annotation;

import dev.railroadide.railroad.ide.sst.ast.expression.NameExpression;

/**
 * An annotation use in the Java AST.
 */
public interface Annotation extends ElementValue {
    /**
     * Returns the annotation type name.
     *
     * @return annotation type name
     */
    NameExpression name();
}
