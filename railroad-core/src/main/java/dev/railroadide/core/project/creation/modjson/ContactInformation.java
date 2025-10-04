package dev.railroadide.core.project.creation.modjson;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.HashMap;

@Data
@EqualsAndHashCode(callSuper = true)
public class ContactInformation extends HashMap<String, String> {
    public String getEmail() {
        return get("email");
    }

    public String getIrc() {
        return get("irc");
    }

    public String getHomepage() {
        return get("homepage");
    }

    public String getIssues() {
        return get("issues");
    }

    public String getSources() {
        return get("sources");
    }

    public void setEmail(String email) {
        put("email", email);
    }

    public void setIrc(String irc) {
        put("irc", irc);
    }

    public void setHomepage(String homepage) {
        put("homepage", homepage);
    }

    public void setIssues(String issues) {
        put("issues", issues);
    }

    public void setSources(String sources) {
        put("sources", sources);
    }
}
