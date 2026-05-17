package dev.railroadide.railroad.ide.diagnostics;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface RegisteredInspection {
    String id();
}
