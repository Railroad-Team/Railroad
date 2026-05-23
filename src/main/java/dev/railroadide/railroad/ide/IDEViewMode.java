package dev.railroadide.railroad.ide;

public enum IDEViewMode {
    CODE("railroad.ide.view_mode.code"),
    GIT("railroad.ide.view_mode.git");

    private final String localizationKey;

    IDEViewMode(String localizationKey) {
        this.localizationKey = localizationKey;
    }

    public String getLocalizationKey() {
        return localizationKey;
    }
}
