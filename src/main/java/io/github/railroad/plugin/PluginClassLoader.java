package io.github.railroad.plugin;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public class PluginClassLoader extends URLClassLoader {
    public PluginClassLoader(Path jarPath) throws IOException {
        super(new URL[]{jarPath.toUri().toURL()}, PluginManager.class.getClassLoader());
    }
}
