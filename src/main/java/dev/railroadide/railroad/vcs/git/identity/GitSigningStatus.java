package dev.railroadide.railroad.vcs.git.identity;

import org.jspecify.annotations.NonNull;

import java.util.StringJoiner;

/**
 * Parsed commit-signing configuration status.
 *
 * @param enabled whether commit signing is enabled
 * @param format configured signing format
 * @param signingKey configured signing key, if any
 */
public record GitSigningStatus(
    boolean enabled,
    Format format,
    String signingKey
) {
    /**
     * Creates signing status from raw git config values.
     *
     * @param gpgSignSetting value of {@code commit.gpgsign}
     * @param gpgFormatSetting value of {@code gpg.format}
     * @param userSigningKey value of {@code user.signingkey}
     * @param gpgProgram value of {@code gpg.program}
     * @return normalized signing status
     */
    public static GitSigningStatus fromGitConfigValues(String gpgSignSetting, String gpgFormatSetting, String userSigningKey, String gpgProgram) {
        boolean enabled = "true".equalsIgnoreCase(gpgSignSetting) || "always".equalsIgnoreCase(gpgSignSetting);
        Format format;
        if ("openpgp".equalsIgnoreCase(gpgFormatSetting)) {
            format = Format.OPENPGP;
        } else if ("ssh".equalsIgnoreCase(gpgFormatSetting)) {
            format = Format.SSH;
        } else {
            format = Format.UNKNOWN;
        }

        String signingKey = (userSigningKey != null && !userSigningKey.isBlank()) ? userSigningKey : null;

        return new GitSigningStatus(enabled, format, signingKey);
    }

    /**
     * Supported signing formats reported by git config.
     */
    public enum Format {
        OPENPGP,
        SSH,
        UNKNOWN
    }

    /**
     * Returns a user-facing description of signing status.
     *
     * @return display string describing enabled state, format, and key
     */
    @Override
    public @NonNull String toString() {
        if (!enabled)
            return "Disabled";

        var joiner = new StringJoiner(", ", "Enabled (", ")");
        joiner.add("Format: " + format);
        if (signingKey != null && !signingKey.isBlank()) {
            joiner.add("Key: " + signingKey);
        } else {
            joiner.add("Key: Not Set");
        }

        return joiner.toString();
    }
}
