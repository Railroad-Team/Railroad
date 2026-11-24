package dev.railroadide.railroad.ide.ui.codeeditor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class JavaImportFoldingTest {
    @Test
    public void findsImportsAcrossCommentsAndGroups() {
        assertEquals(new JavaImportFolding.Range(2, 6), JavaImportFolding.findRange("""
            package example;

            import java.util.List;
            // grouped imports

            import static java.util.Collections.emptyList;
            import example.Other;
            class Example {}
            """));
    }

    @Test
    public void ignoresCommentsAndSingleLineImports() {
        assertNull(JavaImportFolding.findRange("// import fake.Type;\nclass Example {}"));
        assertNull(JavaImportFolding.findRange("import java.util.List;\nclass Example {}"));
        assertNull(JavaImportFolding.findRange("class Example { String text = \"import fake.Type;\"; }"));
    }

    @Test
    public void includesMultilineImportsAndIgnoresIncompleteLastImport() {
        assertEquals(new JavaImportFolding.Range(0, 1), JavaImportFolding.findRange(
            "import java.util.\n    List;\nimport incomplete"));
    }
}
