package io.github.railroad.ide.indexing;

import io.github.railroad.Railroad;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class Indexes {
    private static final String[] KEYWORDS = {
            "abstract", "assert", "boolean", "break", "byte",
            "case", "catch", "char", "class", "const", "continue",
            "default", "do", "double", "else", "enum", "extends",
            "final", "finally", "float", "for", "goto", "if",
            "implements", "import", "instanceof", "int", "interface",
            "long", "native", "new", "package", "private", "protected",
            "public", "return", "short", "static", "strictfp", "super",
            "switch", "synchronized", "this", "throw", "throws", "transient",
            "try", "void", "volatile", "while", "true", "false", "null",
            "var", "sealed", "permits", "non-sealed", "default", "open",
            "module", "requires", "exports", "opens", "to", "uses", "provides",
            "with", "yield", "as", "instanceof", "of", "record"
    };

    public static Trie createTrie() {
        Trie trie = new Trie();
        for (String keyword : KEYWORDS) {
            trie.insert(keyword);
        }

        scanStandardLibrary(trie);

        // print the trie
        trie.print();

        // TODO: Scan project classes and dependencies

        return trie;
    }

    private static void scanStandardLibrary(Trie trie) {
        Path javaHome = Path.of(System.getProperty("java.home"));
        // check if its using java 9 modules
        if (Files.notExists(javaHome.resolve("lib").resolve("modules"))) {
            // We are using java 8 or below so we need to scan the rt.jar
        } else {
            Path jmods = javaHome.resolve("jmods");
            // Scan the `java.base` module
            Path javaBase = jmods.resolve("java.base.jmod"); // this should be effectively a jar file

            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            try(var jmod = new JarFile(javaBase.toFile())) {
                Enumeration<JarEntry> entries = jmod.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String className = entry.getName();
                    if (className.endsWith(".class")) {
                        if(className.contains("module-info.class") || className.contains("package-info.class") || className.contains("com/sun") || className.contains("sun/"))
                            continue;

                        try {
                            Class<?> clazz = classLoader.loadClass(className);
                            trie.insert(clazz.getSimpleName());

                            System.out.println("Loaded class: " + clazz.getSimpleName());
                        } catch (ClassNotFoundException exception) {
                            Railroad.LOGGER.error("Failed to load class: {}", className, exception);
                        }
                    }
                }
            } catch (IOException exception) {
                Railroad.LOGGER.error("Failed to scan standard library", exception);
            }
        }
    }

    public static class StandardLibraryClassLoader extends ClassLoader {
        private final Path jmod;

        public StandardLibraryClassLoader(Path jmod) {
            this.jmod = jmod;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            try (var jmod = new JarFile(this.jmod.toFile())) {
                JarEntry entry = jmod.getJarEntry(name);
                if (entry == null) {
                    throw new ClassNotFoundException(name);
                }

                byte[] bytes = jmod.getInputStream(entry).readAllBytes();

                String className = name.substring("classes/".length()).replace('/', '.');
                return defineClass(className, bytes, 0, bytes.length);
            } catch (IOException exception) {
                throw new ClassNotFoundException(name, exception);
            }
        }
    }
}
