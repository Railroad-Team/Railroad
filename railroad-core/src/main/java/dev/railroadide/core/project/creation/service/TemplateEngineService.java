package dev.railroadide.core.project.creation.service;

import java.util.Map;

public interface TemplateEngineService {
    /**
     * Applies bindings to a template string.
     */
    String apply(String template, Map<String, Object> bindings) throws Exception;
}
