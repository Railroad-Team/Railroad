package dev.railroadide.railroad.utility.compiler;

import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import java.net.URI;
import java.nio.file.Path;

public class JavaSourceFromString extends SimpleJavaFileObject {
    private final String code;

    /**
     * Constructs a new JavaSourceFromString.
     *
     * @param name The name of the compilation unit represented by this file object
     * @param code The source code string
     */
    public JavaSourceFromString(String name, String code) {
        super(URI.create("string:///" + name.replace('.', '/') + JavaFileObject.Kind.SOURCE.extension), JavaFileObject.Kind.SOURCE);
        this.code = code;
    }

    /**
     * Constructs a new JavaSourceFromString using a concrete file path.
     * The path is used only for identification; the provided code is used as the
     * actual contents of the file.
     *
     * @param path The path of the compilation unit
     * @param code The source code string
     */
    public JavaSourceFromString(Path path, String code) {
        super(path.toUri(), JavaFileObject.Kind.SOURCE);
        this.code = code;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return code;
    }
}
