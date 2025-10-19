package dev.railroadide.railroad.project.creation.service;

import dev.railroadide.core.project.creation.service.TemplateEngineService;
import groovy.text.StreamingTemplateEngine;
import groovy.text.Template;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;

public class GroovyTemplateEngineService implements TemplateEngineService {
    private final StreamingTemplateEngine delegate = new StreamingTemplateEngine();

    @Override
    public String apply(String template, Map<String, Object> bindings) throws Exception {
        Template groovyTemplate = delegate.createTemplate(new StringReader(template));
        var writer = new StringWriter();
        groovyTemplate.make(bindings).writeTo(writer);
        return writer.toString();
    }
}
