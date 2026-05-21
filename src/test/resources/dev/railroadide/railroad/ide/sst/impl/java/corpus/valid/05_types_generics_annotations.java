package demo.generics;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.function.Function;

@Target({ElementType.TYPE_USE, ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@interface Marker {
    String value() default "ok";
}

@Marker("container")
class GenericContainer<@Marker("tp") T extends Number & Comparable<T>> {
    private final List<@Marker("list") ? extends T> values;

    GenericContainer(List<? extends T> values) {
        this.values = values;
    }

    <R> R convert(Function<? super T, ? extends R> mapper) throws java.io.IOException {
        try {
            return mapper.apply(values.get(0));
        } catch (RuntimeException | Error ex) {
            throw new java.io.IOException(ex);
        }
    }

    @Marker("array")
    T @Marker("dims") [] snapshot(T sample) {
        return (T[]) new Number[]{sample};
    }
}
