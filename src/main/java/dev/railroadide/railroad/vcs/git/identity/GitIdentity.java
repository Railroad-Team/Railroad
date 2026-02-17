package dev.railroadide.railroad.vcs.git.identity;

/**
 * Effective git identity and signing configuration.
 *
 * @param userName configured git user name
 * @param email configured git user email
 * @param signing signing status
 * @param gitVersion git version string
 */
public record GitIdentity(
    String userName,
    String email,
    GitSigningStatus signing,
    String gitVersion
) {}
