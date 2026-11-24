package dev.railroadide.railroad.ide.runconfig;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EditorMainTest {
    @Test
    public void findsDeclaredEntryPointsAndUsesBinaryNames() {
        assertEquals(List.of(new EditorMain(3, "sample.App"), new EditorMain(6, "sample.App$Nested")),
            EditorMain.find("""
                package sample;
                class App {
                    public static void main(java.lang.String... args) {}
                    String example = "void main()";
                    static class Nested {
                        void main() {}
                    }
                    private static void main() {}
                    static void main(int[] args) {}
                }
                """, "App.java"));
    }

    @Test
    public void compactSourceUsesItsFilename() {
        assertEquals(List.of(new EditorMain(1, "Example")), EditorMain.find("void main() {}", "Example.java"));
    }
}
