package io.github.railroad.settings.ui.themes;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class Theme {
    private String name;
    @SerializedName("download_url") private String downloadUrl;
    private long size;
}
