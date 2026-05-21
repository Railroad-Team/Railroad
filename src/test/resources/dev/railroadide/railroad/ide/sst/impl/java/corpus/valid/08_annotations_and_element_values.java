package demo.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@interface Demo {
    String name();

    int[] versions() default {1, 2, 3};

    Nested nested() default @Nested(flag = true);

    Nested[] nestedArray() default {};
}

@interface Nested {
    boolean flag();
}

@Demo(
        name = "sample",
        versions = {21, 22},
        nested = @Nested(flag = false),
        nestedArray = {
                @Nested(flag = true),
                @Nested(flag = false)
        }
)
class AnnotatedTarget {
}
