package dev.railroadide.railroad.gradle;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses the output of {@code gradle --help} (or similar) to extract command-line options.
 */
public final class GradleHelpParser {
    private GradleHelpParser() {
    }

    public static Map<String, String> parse(byte[] stdout) {
        if (stdout == null || stdout.length == 0)
            return Map.of();

        return parse(new String(stdout, StandardCharsets.UTF_8));
    }

    public static Map<String, String> parse(String helpOutput) {
        if (helpOutput == null || helpOutput.isBlank())
            return Map.of();

        Map<String, String> options = new LinkedHashMap<>();
        String currentOption = null;
        StringBuilder descriptionBuilder = new StringBuilder();

        String[] lines = helpOutput.split("\\R");
        for (String line : lines) {
            if (line == null)
                continue;

            String trimmedLeading = stripLeading(line);
            String optionToken = extractOptionToken(trimmedLeading);
            if (optionToken != null) {
                commitOption(options, currentOption, descriptionBuilder);
                currentOption = optionToken;
                descriptionBuilder.setLength(0);

                String inlineDescription = extractInlineDescription(trimmedLeading);
                if (!inlineDescription.isBlank())
                    descriptionBuilder.append(inlineDescription);

                continue;
            }

            if (currentOption == null)
                continue;

            if (trimmedLeading.isBlank()) {
                commitOption(options, currentOption, descriptionBuilder);
                currentOption = null;
                continue;
            }

            if (!startsWithIndent(line)) {
                commitOption(options, currentOption, descriptionBuilder);
                currentOption = null;
                continue;
            }

            if (!descriptionBuilder.isEmpty())
                descriptionBuilder.append(' ');
            descriptionBuilder.append(trimmedLeading.trim());
        }

        commitOption(options, currentOption, descriptionBuilder);
        return options;
    }

    private static void commitOption(Map<String, String> options, String currentOption, StringBuilder descriptionBuilder) {
        if (currentOption == null)
            return;

        String description = descriptionBuilder.toString().trim();
        options.put(currentOption, description);
        descriptionBuilder.setLength(0);
    }

    private static String stripLeading(String line) {
        int index = 0;
        while (index < line.length() && Character.isWhitespace(line.charAt(index)))
            index++;
        return line.substring(index);
    }

    private static boolean startsWithIndent(String line) {
        return !line.isEmpty() && Character.isWhitespace(line.charAt(0));
    }

    private static String extractOptionToken(String trimmedLine) {
        if (trimmedLine == null || trimmedLine.isEmpty() || trimmedLine.charAt(0) != '-')
            return null;

        String optionSegment = trimmedLine;
        int descriptionStart = findDescriptionStart(trimmedLine);
        if (descriptionStart >= 0)
            optionSegment = trimmedLine.substring(0, descriptionStart);

        for (String token : optionSegment.split(",")) {
            String normalized = token.trim();
            if (!normalized.startsWith("--"))
                continue;

            int spaceIndex = normalized.indexOf(' ');
            if (spaceIndex >= 0)
                normalized = normalized.substring(0, spaceIndex);

            return normalized;
        }

        return null;
    }

    private static String extractInlineDescription(String trimmedLine) {
        int descriptionStart = findDescriptionStart(trimmedLine);
        if (descriptionStart < 0 || descriptionStart >= trimmedLine.length())
            return "";

        return trimmedLine.substring(descriptionStart).trim();
    }

    private static int findDescriptionStart(String line) {
        for (int i = 0; i < line.length() - 1; i++) {
            if (line.charAt(i) == ' ' && line.charAt(i + 1) == ' ')
                return i;
        }

        return -1;
    }
}
