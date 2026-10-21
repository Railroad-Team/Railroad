package dev.railroadide.railroad.project.creation.modjson;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.HashMap;

/**
 * Contact metadata with convenience accessors for standard Fabric contact keys.
 * Additional contact keys can be stored using the inherited map operations.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ContactInformation extends HashMap<String, String> {
    /**
     * Returns the contact email address.
     *
     * @return the email address, or {@code null} if absent
     */
    public String getEmail() {
        return get("email");
    }

    /**
     * Returns the IRC contact location.
     *
     * @return the IRC location, or {@code null} if absent
     */
    public String getIrc() {
        return get("irc");
    }

    /**
     * Returns the project's homepage URL.
     *
     * @return the homepage URL, or {@code null} if absent
     */
    public String getHomepage() {
        return get("homepage");
    }

    /**
     * Returns the project's issue tracker URL.
     *
     * @return the issue tracker URL, or {@code null} if absent
     */
    public String getIssues() {
        return get("issues");
    }

    /**
     * Returns the project's source code URL.
     *
     * @return the source code URL, or {@code null} if absent
     */
    public String getSources() {
        return get("sources");
    }

    /**
     * Stores the contact email address.
     *
     * @param email the email address
     */
    public void setEmail(String email) {
        put("email", email);
    }

    /**
     * Stores the IRC contact location.
     *
     * @param irc the IRC location
     */
    public void setIrc(String irc) {
        put("irc", irc);
    }

    /**
     * Stores the project's homepage URL.
     *
     * @param homepage the homepage URL
     */
    public void setHomepage(String homepage) {
        put("homepage", homepage);
    }

    /**
     * Stores the project's issue tracker URL.
     *
     * @param issues the issue tracker URL
     */
    public void setIssues(String issues) {
        put("issues", issues);
    }

    /**
     * Stores the project's source code URL.
     *
     * @param sources the source code URL
     */
    public void setSources(String sources) {
        put("sources", sources);
    }
}
