package dev.railroadide.railroad.project.creation.service;

import java.util.Map;

/**
 * Renders project templates using named values.
 */
public interface TemplateEngineService {
    /**
     * Applies bindings to a template string.
     *
     * @param template the template source to render
     * @param bindings the values available by name in the template
     * @return the rendered template text
     * @throws Exception if the template cannot be compiled or rendered
     */
    String apply(String template, Map<String, Object> bindings) throws Exception;
}
