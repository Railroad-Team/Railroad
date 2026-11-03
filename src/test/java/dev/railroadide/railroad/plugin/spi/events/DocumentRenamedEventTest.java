package dev.railroadide.railroad.plugin.spi.events;

import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.plugin.spi.dto.Document;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DocumentRenamedEventTest {
    @Test
    public void legacyConstructorSupportsNonFileDocuments() {
        String[][] cases = {
            {"memory:///folder/new.md", "memory:///folder/old%20name.md"},
            {"generated:///folder/new.md?source=a/b#part", "generated:///folder/old%20name.md?source=a/b#part"},
            {"jar:file:///archive.jar!/folder/new.md", "jar:file:///archive.jar!/folder/old%20name.md"},
            {"untitled:document", "untitled:document"}
        };
        for (String[] sample : cases) {
            assertPreviousLocation(sample[0], sample[1]);
        }
    }

    private void assertPreviousLocation(String location, String expected) {
        Document document = new Document() {
            @Override
            public DocumentUri getUri() {
                return DocumentUri.parse(location);
            }
            @Override
            public String getName() {
                return "new.md";
            }
            @Override
            public byte[] getContent() {
                return new byte[0];
            }
            @Override
            public long getLineCount() {
                return 0;
            }
            @Override
            public String getLanguageId() {
                return "markdown";
            }
            @Override
            public boolean isDirty() {
                return false;
            }
        };
        var event = new DocumentRenamedEvent(document, "old name.md", "new.md");
        assertEquals(DocumentUri.parse(expected), event.previousUri());
    }
}
