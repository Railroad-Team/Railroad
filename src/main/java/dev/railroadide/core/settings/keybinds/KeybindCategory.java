package dev.railroadide.core.settings.keybinds;

/**
 * Represents a category of keybinds.
 * @param id The id, which is used to distinguish between categories and correctly group them.
 * @param titleKey
 */
public record KeybindCategory(String id, String titleKey) {
    public static KeybindCategory of(String id, String titleKey) {
        return new KeybindCategory(id, titleKey);
    }
}
