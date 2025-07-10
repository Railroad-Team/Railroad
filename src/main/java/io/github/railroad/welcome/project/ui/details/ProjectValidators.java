package io.github.railroad.welcome.project.ui.details;

import io.github.railroad.Railroad;
import io.github.railroad.core.form.ValidationResult;
import io.github.railroad.core.utility.StringUtils;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public class ProjectValidators {
    private static final String USER_AGENT = "Mozilla/5.0 (Windows; U; Windows NT 6.0; en-US; rv:1.9.1.2) Gecko/20090729 Firefox/3.5.2 (.NET CLR 3.5.30729)";

    public static ValidationResult validatePath(TextField field) {
        String text = field.getText();
        if (text == null || text.isBlank())
            return ValidationResult.error("railroad.project.creation.location.error.required");

        if (!text.matches(".*[a-zA-Z0-9]"))
            return ValidationResult.error("railroad.project.creation.location.error.invalid_characters");

        try {
            Path path = Path.of(text);
            if (Files.notExists(path)) {
                // Create the directory if it doesn't exist
                try {
                    Files.createDirectories(path);
                } catch (IOException e) {
                    return ValidationResult.error("railroad.project.creation.location.error.cannot_create");
                }
            }

            if (!Files.isDirectory(path))
                return ValidationResult.error("railroad.project.creation.location.error.not_directory");

            try (var stream = Files.newDirectoryStream(path)) {
                if (stream.iterator().hasNext())
                    return ValidationResult.warning("railroad.project.creation.location.warning.not_empty");
            } catch (IOException ignored) {
                return ValidationResult.error("railroad.project.creation.location.error.not_readable");
            }
        } catch (InvalidPathException exception) {
            return ValidationResult.error("railroad.project.creation.location.error.invalid_path");
        }

        if (text.contains("OneDrive"))
            return ValidationResult.warning("railroad.project.creation.location.warning.onedrive");

        return ValidationResult.ok();
    }

    public static ValidationResult validateProjectName(TextField field) {
        String text = field.getText();
        if (text == null || text.isBlank())
            return ValidationResult.error("railroad.project.creation.name.error.required");

        if (text.length() > 256)
            return ValidationResult.error("railroad.project.creation.name.error.length_long");

        if (text.length() < 3)
            return ValidationResult.error("railroad.project.creation.name.error.length_short");

        if (text.matches("[.<>:\"/\\\\|?*]"))
            return ValidationResult.error("railroad.project.creation.name.error.invalid_characters");

        return ValidationResult.ok();
    }

    public static ValidationResult validateCustomLicense(TextField field) {
        if (field.getText().isBlank())
            return ValidationResult.error("railroad.project.creation.license.custom.error.required");

        if (field.getText().length() > 2048)
            return ValidationResult.error("railroad.project.creation.license.custom.error.length_long");

        return ValidationResult.ok();
    }

    public static ValidationResult validateModId(TextField field) {
        String text = field.getText();
        if (text == null || text.isBlank())
            return ValidationResult.error("railroad.project.creation.mod_id.error.required");

        if (text.length() < 3)
            return ValidationResult.error("railroad.project.creation.mod_id.error.length_short");

        if (text.length() > 64)
            return ValidationResult.error("railroad.project.creation.mod_id.error.length_long");

        if (!text.matches("^[a-z][a-z0-9_]{1,63}$"))
            return ValidationResult.error("railroad.project.creation.mod_id.error.invalid_characters");

        return ValidationResult.ok();
    }

    public static ValidationResult validateModName(TextField field) {
        String text = field.getText();
        if (text == null || text.isBlank())
            return ValidationResult.error("railroad.project.creation.mod_name.error.required");

        if (text.length() > 256)
            return ValidationResult.error("railroad.project.creation.mod_name.error.length_long");

        if (text.length() < 5)
            return ValidationResult.warning("railroad.project.creation.mod_name.error.length_short");

        return ValidationResult.ok();
    }

    public static ValidationResult validateMainClass(TextField field) {
        String text = field.getText();
        if (text == null || text.isBlank())
            return ValidationResult.error("railroad.project.creation.main_class.error.required");

        if (!text.matches("[a-zA-Z0-9_]+"))
            return ValidationResult.error("railroad.project.creation.main_class.error.invalid_characters");

        return ValidationResult.ok();
    }

    public static ValidationResult validateAuthor(TextField field) {
        String text = field.getText();
        if (text.length() > 256)
            return ValidationResult.error("railroad.project.creation.author.error.length_long");

        if (text.length() < 3)
            return ValidationResult.warning("railroad.project.creation.author.error.length_short");

        return ValidationResult.ok();
    }

    public static ValidationResult validateCredits(TextField field) {
        if (field.getText().length() > 256)
            return ValidationResult.error("railroad.project.creation.credits.error.length_long");

        return ValidationResult.ok();
    }

    public static ValidationResult validateDescription(TextArea area) {
        String text = area.getText();
        if (text == null || text.isBlank())
            return ValidationResult.error("railroad.project.creation.description.error.required");

        if (text.length() > 2048)
            return ValidationResult.error("railroad.project.creation.description.error.length_long");

        if (text.length() < 10)
            return ValidationResult.warning("railroad.project.creation.description.error.length_short");

        return ValidationResult.ok();
    }

    public static ValidationResult validateIssues(TextField field) {
        String text = field.getText();
        if (text.length() > 256)
            return ValidationResult.error("railroad.project.creation.issues.error.length_long");

        if (!text.isBlank()) {
            if (!text.matches(StringUtils.URL_REGEX))
                return ValidationResult.warning("railroad.project.creation.issues.error.invalid_url");

            if (text.contains("github.com") && !text.contains("/issues"))
                return ValidationResult.warning("railroad.project.creation.issues.error.no_issues");

            try (Response response = Railroad.HTTP_CLIENT_NO_FOLLOW.newCall(new Request.Builder()
                    .url(text)
                    .head()
                    .header("User-Agent", USER_AGENT)
                    .build()).execute()) {
                if (response.code() != HttpURLConnection.HTTP_OK)
                    return ValidationResult.warning("railroad.project.creation.issues.error.invalid_url");
            } catch (IOException exception) {
                return ValidationResult.warning("railroad.project.creation.issues.error.invalid_url");
            }
        }

        return ValidationResult.ok();
    }

    public static ValidationResult validateUpdateJsonUrl(TextField field) {
        String text = field.getText();
        if (text.length() > 256)
            return ValidationResult.error("railroad.project.creation.update_json.error.length_long");

        if (!text.isBlank()) {
            if (!text.matches(StringUtils.URL_REGEX))
                return ValidationResult.warning("railroad.project.creation.update_json.error.invalid_url");

            try (Response response = Railroad.HTTP_CLIENT_NO_FOLLOW.newCall(new Request.Builder()
                    .url(text)
                    .head()
                    .header("User-Agent", USER_AGENT)
                    .build()).execute()) {
                if (response.code() != HttpURLConnection.HTTP_OK)
                    return ValidationResult.warning("railroad.project.creation.update_json.error.invalid_url");
            } catch (IOException exception) {
                return ValidationResult.warning("railroad.project.creation.update_json.error.invalid_url");
            }

            if (!text.endsWith(".json"))
                return ValidationResult.warning("railroad.project.creation.update_json.error.invalid_extension");
        }

        return ValidationResult.ok();
    }

    public static ValidationResult validateGenericUrl(TextField field, String errorKey) {
        String text = field.getText();
        if (text.length() > 256)
            return ValidationResult.error("railroad.project.creation." + errorKey + ".error.length_long");

        if (!text.isBlank()) {
            if (!text.matches(StringUtils.URL_REGEX))
                return ValidationResult.warning("railroad.project.creation." + errorKey + ".error.invalid_url");

            try (Response response = Railroad.HTTP_CLIENT_NO_FOLLOW.newCall(new Request.Builder()
                    .url(text)
                    .head()
                    .header("User-Agent", USER_AGENT)
                    .build()).execute()) {
                if (response.code() != HttpURLConnection.HTTP_OK)
                    return ValidationResult.warning("railroad.project.creation." + errorKey + ".error.invalid_url");
            } catch (IOException exception) {
                return ValidationResult.warning("railroad.project.creation." + errorKey + ".error.invalid_url");
            }
        }

        return ValidationResult.ok();
    }

    public static ValidationResult validateGroupId(TextField field) {
        String text = field.getText();
        if (text == null || text.isBlank())
            return ValidationResult.error("railroad.project.creation.group_id.error.required");

        if (text.length() > 256)
            return ValidationResult.error("railroad.project.creation.group_id.error.length_long");

        if (!text.matches("[a-zA-Z0-9.]+"))
            return ValidationResult.error("railroad.project.creation.group_id.error.invalid_characters");

        return ValidationResult.ok();
    }

    public static ValidationResult validateArtifactId(TextField field) {
        String text = field.getText();
        if (text == null || text.isBlank())
            return ValidationResult.error("railroad.project.creation.artifact_id.error.required");

        if (text.length() > 256)
            return ValidationResult.error("railroad.project.creation.artifact_id.error.length_long");

        if (!text.matches("[a-z0-9-]+"))
            return ValidationResult.error("railroad.project.creation.artifact_id.error.invalid_characters");

        return ValidationResult.ok();
    }

    public static ValidationResult validateVersion(TextField field) {
        String text = field.getText();
        if (text == null || text.isBlank())
            return ValidationResult.error("railroad.project.creation.version.error.required");

        if (text.length() > 256)
            return ValidationResult.error("railroad.project.creation.version.error.length_long");

        if (!text.matches("[a-zA-Z0-9.-]+"))
            return ValidationResult.error("railroad.project.creation.version.error.invalid_characters");

        return ValidationResult.ok();
    }

    public static String getRepairedPath(String path) {
        while (path.endsWith(" "))
            path = path.substring(0, path.length() - 1);

        path = path.replace("/", "\\");

        // Remove trailing backslashes
        while (path.endsWith("\\"))
            path = path.substring(0, path.length() - 1);

        // remove any whitespace before a backslash
        path = path.replaceAll("\\s+\\\\", "\\");

        // remove any whitespace after a backslash
        path = path.replaceAll("\\\\\\\\s+", "\\\\");

        // remove any double backslashes
        path = path.replaceAll("\\\\\\\\", "\\\\");

        // remove any trailing whitespace
        path = path.trim();

        return path;
    }

    public static BooleanBinding createBinding(BooleanProperty property) {
        return Bindings.when(property).then(true).otherwise(false);
    }
}
