package dev.railroadide.railroad.vcs.git.execution.progress;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses git stdout/stderr lines into typed progress events.
 */
public final class GitProgressParser {
    private GitProgressParser() {
    }

    // Examples:
    // "Receiving objects:  42% (1234/5678), 1.23 MiB | 4.56 MiB/s"
    // "Resolving deltas:  90% (111/123)"
    private static final Pattern PERCENT_LINE = Pattern.compile(
        "^(?<phase>[A-Za-z ][A-Za-z ]+?):\\s*(?<pct>\\d{1,3})%\\b.*$"
    );

    // Examples:
    // "Enumerating objects: 123, done."
    // "Counting objects: 100% (123/123), done."
    // (If it has % it will match PERCENT_LINE too)
    private static final Pattern PHASE_PREFIX = Pattern.compile(
        "^(?<phase>[A-Za-z ][A-Za-z ]+?):\\s*.*$"
    );

    // Examples:
    // "remote: Enumerating objects: 123, done."
    // "remote: Total 123 (delta 45), reused 0 (delta 0), pack-reused 0"
    private static final Pattern REMOTE_PREFIX = Pattern.compile("^remote:\\s*(?<msg>.*)$");

    /**
     * Attempts to parse a single git output line into a progress event.
     *
     * @param line output line text
     * @param currentPhase currently known phase name
     * @return parsed event when recognized, otherwise an empty optional
     */
    public static Optional<GitProgressEvent> tryParse(String line, String currentPhase) {
        if (line == null)
            return Optional.empty();

        String normalized = line.trim();
        if (normalized.isEmpty())
            return Optional.empty();

        // Special cases where we just want to emit a message
        if (normalized.startsWith("From ") || normalized.startsWith("* ") || normalized.startsWith(" + ") || normalized.startsWith(" = "))
            return Optional.of(new GitProgressEvent.Message(normalized));

        // Check for remote prefix
        Matcher remoteMatcher = REMOTE_PREFIX.matcher(normalized);
        if (remoteMatcher.matches()) {
            String msg = remoteMatcher.group("msg").trim();
            // Try to parse nested progress info
            Optional<GitProgressEvent> nested = tryParse(msg, currentPhase);
            return nested.isPresent() ? nested : Optional.of(new GitProgressEvent.Message(msg));
        }

        // Check for percentage line
        Matcher percentageMatcher = PERCENT_LINE.matcher(normalized);
        if (percentageMatcher.matches()) {
            String phase = normalizePhase(percentageMatcher.group("phase"));
            int percent = clampPercent(parseIntSafe(percentageMatcher.group("pct"), -1));
            if (percent >= 0)
                return Optional.of(new GitProgressEvent.Percentage(phase, percent));
        }

        // Check for phase prefix
        Matcher phaseMatcher = PHASE_PREFIX.matcher(normalized);
        if (phaseMatcher.matches()) {
            String phase = normalizePhase(phaseMatcher.group("phase"));
            return Optional.of(new GitProgressEvent.Phase(phase));
        }

        // Fallback to message
        return Optional.of(new GitProgressEvent.Message(normalized));
    }

    private static String normalizePhase(String phase) {
        if (phase == null)
            return "(unknown)";

        String normalized = phase.trim();
        if (normalized.isEmpty())
            return "(unknown)";

        return normalized.replaceAll("\\s+", " ");
    }

    private static int parseIntSafe(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int clampPercent(int percent) {
        return Math.clamp(percent, 0, 100);
    }
}
