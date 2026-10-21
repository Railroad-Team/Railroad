package dev.railroadide.railroad.vcs.connections;

import io.github.palexdev.mfxresources.fonts.IconProvider;
import io.github.palexdev.mfxresources.fonts.IconsProviders;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

/**
 * Represents a type of profile in the version control system.
 * This class encapsulates the name, icon, and icon provider for a profile type.
 */
@Getter
public final class ProfileType {
    /**
     * Display name identifying the profile type.
     *
     * @return the name supplied at construction
     */
    private final String name;
    /**
     * Icon code used to represent the profile type.
     *
     * @return the icon code supplied at construction
     */
    private final String icon;
    /**
     * Provider used to resolve the profile type's icon code.
     *
     * @param iconProvider replacement icon provider
     * @return the currently configured icon provider
     */
    @Setter
    private IconProvider iconProvider;

    /**
     * Constructs a ProfileType with the specified name, icon, and icon provider.
     *
     * @param name The name of the profile type.
     * @param icon The icon associated with the profile type.
     * @param iconProvider The icon provider for the profile type.
     */
    public ProfileType(String name, String icon, IconProvider iconProvider) {
        this.name = name;
        this.icon = icon;
        this.iconProvider = iconProvider;
    }

    /**
     * Constructs a ProfileType with the specified name and icon, using the default icon provider.
     *
     * @param name The name of the profile type.
     * @param icon The icon associated with the profile type.
     */
    public ProfileType(String name, String icon) {
        this(name, icon, IconsProviders.defaultProvider());
    }

    /**
     * Compares the name, icon code, and current icon provider.
     *
     * @param obj object to compare
     * @return true if the other object is a profile type with equal field values
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj == null || obj.getClass() != this.getClass())
            return false;
        var that = (ProfileType) obj;
        return Objects.equals(this.name, that.name) &&
            Objects.equals(this.icon, that.icon) &&
            Objects.equals(this.iconProvider, that.iconProvider);
    }

    /**
     * Computes a hash from the name, icon code, and current icon provider.
     *
     * @return the hash code, which may change when the icon provider changes
     */
    @Override
    public int hashCode() {
        return Objects.hash(name, icon, iconProvider);
    }

    /**
     * Describes the profile type's name and icon code for diagnostics.
     *
     * @return the profile type description
     */
    @Override
    public String toString() {
        return "ProfileType[" +
            "name=" + name + ", " +
            "icon=" + icon + ']';
    }
}
