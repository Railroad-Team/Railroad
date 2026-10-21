package dev.railroadide.railroad.settings.keybinds;

/**
 * Represents a category of keybinds.
 *
 * @param id The id, which is used to distinguish between categories and correctly group them.
 * @param titleKey localization key used for the category's display title
 */
public record KeybindCategory(String id, String titleKey) {
}
