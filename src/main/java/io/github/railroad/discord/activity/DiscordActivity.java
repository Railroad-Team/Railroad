package io.github.railroad.discord.activity;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
public class DiscordActivity {
    private final DiscordActivityTimestamps timestamps;
    private final DiscordActivityAssets assets;
    private final DiscordActivityParty party;
    private transient final DiscordActivitySecrets secretsBak;
    private transient final List<DiscordActivityButton> buttonsBak;
    private Long applicationId;
    @Setter
    private String name;
    private int type;
    @Setter
    private String state;
    @Setter
    private String details;
    @Setter
    private boolean instance;
    private List<DiscordActivityButton> buttons;
    private DiscordActivitySecrets secrets;

    public DiscordActivity() {
        this.timestamps = new DiscordActivityTimestamps();
        this.assets = new DiscordActivityAssets();
        this.party = new DiscordActivityParty();

        this.secretsBak = new DiscordActivitySecrets();
        this.buttonsBak = new ArrayList<>();
        setActivityButtonsMode(ActivityButtonsMode.SECRETS);
    }

    public void setApplicationId(long applicationId) {
        this.applicationId = applicationId;
    }

    public ActivityType getType() {
        return ActivityType.values()[type];
    }

    public void setType(ActivityType type) {
        this.type = type.ordinal();
    }

    public List<DiscordActivityButton> getButtons() {
        return Collections.unmodifiableList(buttonsBak);
    }

    public void addButton(DiscordActivityButton button) {
        if (buttonsBak.size() == 2)
            throw new IllegalStateException("Cannot add more than 2 buttons");

        buttonsBak.add(button);
    }

    public boolean removeButton(DiscordActivityButton button) {
        return buttons.remove(button);
    }

    public ActivityButtonsMode getActivityButtonsMode() {
        return buttons != null ? ActivityButtonsMode.BUTTONS : ActivityButtonsMode.SECRETS;
    }

    /**
     * <p>Changes the button display mode</p>
     * <p>Only custom buttons (ActivityButtonsMode.BUTTONS) or "Ask to join"/"Spectate" (ActivityButtonsMode.SECRETS) buttons can be displayed at the same time</p>
     *
     * @param mode button mode
     */
    public void setActivityButtonsMode(ActivityButtonsMode mode) {
        if (mode == ActivityButtonsMode.SECRETS) {
            this.buttons = null;
            this.secrets = secretsBak;
        } else {
            this.secrets = null;
            this.buttons = buttonsBak;
        }
    }

    @Override
    public String toString() {
        return "DiscordActivity{" +
                "timestamps=" + timestamps +
                ", assets=" + assets +
                ", party=" + party +
                ", secretsBak=" + secretsBak +
                ", buttonsBak=" + buttonsBak +
                ", applicationId=" + applicationId +
                ", name='" + name + '\'' +
                ", type=" + type +
                ", state='" + state + '\'' +
                ", details='" + details + '\'' +
                ", instance=" + instance +
                ", buttons=" + buttons +
                ", secrets=" + secrets +
                '}';
    }

    public enum ActivityButtonsMode {
        BUTTONS,
        SECRETS
    }

    public enum ActivityType {
        PLAYING,
        STREAMING,
        LISTENING,
        UNUSED,
        CUSTOM,
        COMPETING
    }
}
