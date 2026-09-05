package dev.railroadide.railroad.theme;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

/**
 * Represents a theme with its name, download URL, and size.
 */
@Data
public class Theme {
    private String name;
    @SerializedName("download_url")
    private String downloadUrl;
    private long size;
}
