package io.github.railroad.ide.indexing;

import io.github.railroad.Railroad;
import io.github.railroad.locomotive.Main;
import io.github.railroad.locomotive.PacketHelper;
import io.github.railroad.locomotive.Version;
import io.github.railroad.locomotive.packet.Packet;
import io.github.railroad.locomotive.packet.PacketMethod;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Optional;
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
        new Thread(() -> Main.main(new String[0])).start(); // TODO: Replace with starting up a service
        LocomotiveHandler.INSTANCE.listen();

        Path javaHome = Path.of(System.getProperty("java.home"));
        // check if its using java 9 modules
        if (Files.notExists(javaHome.resolve("lib").resolve("modules"))) {
            // We are using java 8 or below so we need to scan the rt.jar
        } else {
            Path jmods = javaHome.resolve("jmods");
            // Scan the `java.base` module
            Path javaBase = jmods.resolve("java.base.jmod"); // this should be effectively a jar file

            try(var jmod = new JarFile(javaBase.toFile())) {
                Enumeration<JarEntry> entries = jmod.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String className = entry.getName();
                    if (className.startsWith("classes/java/") && className.endsWith(".class")) {
                        className = className.substring("classes/java/".length(), className.length() - ".class".length());
                        if(className.endsWith("module-info") || className.endsWith("package-info"))
                            continue;

                        className = className.replace("/", ".");
                        trie.insert(className);
                    }
                }
            } catch (IOException exception) {
                Railroad.LOGGER.error("Failed to scan standard library", exception);
            }
        }
    }

    public static class LocomotiveHandler {
        private static final String HOST = "localhost"; // TODO: Configurable????
        private static final int PORT = 29687; // TODO: Configurable?

        public static final LocomotiveHandler INSTANCE = new LocomotiveHandler();

        private boolean isListening = false;

        public void listen() {
            if (isListening)
                return;

            isListening = true;
            new Thread(this::run).start();
        }

        private void run() {
            try (var serverSocket = new Socket(HOST, PORT)) {
                System.out.println("Connected to server on port " + PORT);

                InputStream inputStream = serverSocket.getInputStream();
                OutputStream outputStream = serverSocket.getOutputStream();

                long start = System.currentTimeMillis();
                PacketHelper.sendPacket(outputStream, Version.VERSION_1, PacketMethod.PING, new byte[0]);
                Optional<Packet> pingResponse = Optional.empty();
                while (pingResponse.isEmpty()) {
                    try {
                        pingResponse = PacketHelper.readPacket(inputStream);
                    } catch (IOException ignored) {
                        Thread.onSpinWait();
                    }
                }

                Packet response = pingResponse.get();
                if (response.getPacketMethod() != PacketMethod.PING) {
                    System.err.println("Received invalid response from server: " + response.getPacketMethod());
                    return;
                }

                long end = System.currentTimeMillis();
                System.out.println("Ping response received in " + (end - start) + "ms");

                while(serverSocket.isConnected()) {
                    Thread.onSpinWait(); // TODO
                }
            } catch (IOException exception) {
                System.err.println("Error starting server: " + exception.getMessage());
                exception.printStackTrace();
            }
        }
    }
}
