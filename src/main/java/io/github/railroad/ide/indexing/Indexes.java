package io.github.railroad.ide.indexing;

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

            try(var jmod = new JarFile(javaBase.toFile())) {
                ClassLoader jdkClassLoader = ClassLoader.getSystemClassLoader();
                Enumeration<JarEntry> entries = jmod.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.getName().endsWith(".class")) {
                        // load the class file and scan it for methods and fields

                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
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
            return super.findClass(name);
        }
    }
}
