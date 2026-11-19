package dev.railroadide.railroad.ide.completion;

import dev.railroadide.railroad.ide.signature.SignatureHelp;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CompletionResultTest {
    @Test
    public void completionCandidatesAndSignatureParametersAreImmutableSnapshots() {
        var item = new CompletionItem("value", "value");
        var items = new ArrayList<>(List.of(item));
        var completion = new CompletionResult(0, items);
        items.clear();
        assertEquals(List.of(item), completion.items());
        assertThrows(UnsupportedOperationException.class, () -> completion.items().clear());
        assertThrows(IllegalArgumentException.class, () -> new CompletionResult(-1, List.of(item)));

        var parameter = new SignatureHelp.ParameterInfo("text", "value", false);
        var parameters = new ArrayList<>(List.of(parameter));
        var signature = new SignatureHelp("", "", "print", parameters, 0, false, "unit", false);
        parameters.clear();
        assertEquals(List.of(parameter), signature.parameters());
        assertThrows(UnsupportedOperationException.class, () -> signature.parameters().clear());
    }

    @Test
    public void genericProvidersCanTriggerPrefixCompletionWithoutJavaPunctuation() {
        CompletionProvider provider = (_, _) -> CompletionResult.empty();
        assertTrue(provider.shouldTrigger("name"));
        assertFalse(provider.shouldTrigger(""));
        var javaProvider = new JdtCompletionProvider(new String[0]);
        assertTrue(javaProvider.shouldTrigger("."));
        assertFalse(javaProvider.shouldTrigger(":"));
        assertFalse(javaProvider.shouldTrigger("name"));
    }
}
