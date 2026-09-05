package dev.railroadide.railroad.project.onboarding;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.form.ValidationResult;
import dev.railroadide.railroad.utility.StringUtils;
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
import java.util.Locale;
import java.util.jar.JarFile;

// TODO: These are not all project creation specific, so they should be moved and modified to a more general util class
/**
 * Validation and naming helpers used by project onboarding forms.
 * Path validators may create directories while probing a location, and URL validators perform synchronous HTTP
 * requests.
 */
public class ProjectValidators {
    private static final String USER_AGENT = "Mozilla/5.0 (Windows; U; Windows NT 6.0; en-US; rv:1.9.1.2) Gecko/20090729 Firefox/3.5.2 (.NET CLR 3.5.30729)";

    /**
     * Checks directory syntax, readability, and emptiness, warning about OneDrive paths.
     * For an absent path, the creation probe creates directories and removes the leaf directory before checking its
     * type.
     *
     * @param field text field containing the value to validate
     * @return the validation status and localized message, if any
     */
    public static ValidationResult validateDirectoryPath(TextField field) {
        return validatePath(field, true);
    }

    /**
     * Checks a directory path and warns when neither {@code build.gradle} nor {@code build.gradle.kts} exists.
     * This performs the same filesystem probe as {@link #validateDirectoryPath(TextField)}.
     *
     * @param field text field containing the value to validate
     * @return the validation status and localized message, if any
     */
    public static ValidationResult validateGradleProjectPath(TextField field) {
        ValidationResult result = validateDirectoryPath(field);
        if (result.status() == ValidationResult.Status.ERROR)
            return result;

        String text = field.getText();
        Path path = Path.of(text);
        if (Files.notExists(path.resolve("build.gradle")) && Files.notExists(path.resolve("build.gradle.kts")))
            return ValidationResult.warning("railroad.project.creation.location.warning.not_gradle_project");

        return ValidationResult.ok();
    }

    /**
     * Checks a file path and an optional case-sensitive extension.
     * For an absent file, missing parent directories may be created during validation.
     *
     * @param field text field containing the value to validate
     * @param extension required suffix without the dot, or {@code null} or blank to skip the suffix check
     * @return the validation status and localized message, if any
     */
    public static ValidationResult validateFilePath(TextField field, String extension) {
        ValidationResult result = validatePath(field, false);
        if (result.status() == ValidationResult.Status.ERROR)
            return result;

        String text = field.getText();
        if (extension != null && !extension.isBlank() && !text.endsWith("." + extension))
            return ValidationResult.error("railroad.project.creation.location.error.invalid_extension");

        return ValidationResult.ok();
    }

    /**
     * Checks a {@code .jar} path and attempts to open it as a JAR archive.
     *
     * @param field text field containing the value to validate
     * @return the validation status and localized message, if any
     */
    public static ValidationResult validateJarFilePath(TextField field) {
        ValidationResult result = validateFilePath(field, "jar");
        if (result.status() == ValidationResult.Status.ERROR)
            return result;

        String text = field.getText();
        if (!text.endsWith(".jar"))
            return result;

        Path path = Path.of(text);
        try (var _ = new JarFile(path.toFile())) {
            return ValidationResult.ok();
        } catch (IOException exception) {
            return ValidationResult.error("railroad.project.creation.location.error.invalid_jar");
        }
    }

    private static ValidationResult validatePath(TextField field, boolean expectDirectory) {
        String text = field.getText();
        if (text == null || text.isBlank())
            return ValidationResult.error("railroad.project.creation.location.error.required");

        Path path;
        try {
            path = Path.of(text);
        } catch (InvalidPathException exception) {
            return ValidationResult.error("railroad.project.creation.location.error.invalid_path");
        }

        if (Files.notExists(path)) {
            try {
                if (expectDirectory) {
                    Files.createDirectories(path);
                } else {
                    Path parent = path.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                }

                Files.deleteIfExists(path);
            } catch (IOException _) {
                return ValidationResult.error("railroad.project.creation.location.error.cannot_create");
            }
        }

        if (expectDirectory) {
            if (!Files.isDirectory(path))
                return ValidationResult.error("railroad.project.creation.location.error.not_directory");

            try (var stream = Files.newDirectoryStream(path)) {
                // TODO: Do not validate if the directory is empty because we're going to create a new directory inside
                // it
                if (stream.iterator().hasNext())
                    return ValidationResult.warning("railroad.project.creation.location.warning.not_empty");
            } catch (IOException _) {
                return ValidationResult.error("railroad.project.creation.location.error.not_readable");
            }
        } else if (Files.isDirectory(path))
            return ValidationResult.error("railroad.project.creation.location.error.is_directory");

        if (text.contains("OneDrive"))
            return ValidationResult.warning("railroad.project.creation.location.warning.onedrive");

        return ValidationResult.ok();
    }

    /**
     * Checks that a project name is present and between 3 and 256 characters, then applies the filename-character
     * check.
     *
     * @param field text field containing the value to validate
     * @return the validation status and localized message, if any
     */
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

    /**
     * Requires nonblank custom license text of at most 2048 characters.
     *
     * @param field text field containing the value to validate
     * @return the validation status and localized message, if any
     */
    public static ValidationResult validateCustomLicense(TextField field) {
        if (field.getText().isBlank())
            return ValidationResult.error("railroad.project.creation.license.custom.error.required");

        if (field.getText().length() > 2048)
            return ValidationResult.error("railroad.project.creation.license.custom.error.length_long");

        return ValidationResult.ok();
    }

    /**
     * Requires a 3-to-64-character mod identifier starting with a lowercase letter and containing only lowercase
     * letters, digits, and underscores.
     *
     * @param field text field containing the value to validate
     * @return the validation status and localized message, if any
     */
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

    /**
     * Requires a mod name of at most 256 characters and warns when it has fewer than five characters.
     *
     * @param field text field containing the value to validate
     * @return the validation status and localized message, if any
     */
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

    /**
     * Requires a nonblank main class name containing only ASCII letters, digits, and underscores.
     *
     * @param field text field containing the value to validate
     * @return the validation status and localized message, if any
     */
    public static ValidationResult validateMainClass(TextField field) {
        String text = field.getText();
        if (text == null || text.isBlank())
            return ValidationResult.error("railroad.project.creation.main_class.error.required");

        if (!text.matches("[a-zA-Z0-9_]+"))
            return ValidationResult.error("railroad.project.creation.main_class.error.invalid_characters");

        return ValidationResult.ok();
    }

    /**
     * Checks a nonblank class name consisting of dot-separated ASCII identifier segments.
     *
     * @param field text field containing the value to validate
     * @return the validation status and localized message, if any
     */
    public static ValidationResult validateQualifiedMainClass(TextField field) {
        String text = field.getText();
        if (text == null || text.isBlank())
            return ValidationResult.error("railroad.project.creation.qualified_main_class.error.required");

        if (!text.matches("([a-zA-Z_][a-zA-Z0-9_]*\\.)*[a-zA-Z_][a-zA-Z0-9_]*"))
            return ValidationResult.error("railroad.project.creation.qualified_main_class.error.invalid_format");

        return ValidationResult.ok();
    }

    /**
     * Accepts an empty author, rejects more than 256 characters, and warns for nonblank names shorter than three
     * characters.
     *
     * @param field text field containing the value to validate
     * @return the validation status and localized message, if any
     */
    public static ValidationResult validateAuthor(TextField field) {
        String text = field.getText();
        if (text.isBlank())
            return ValidationResult.ok();

        if (text.length() > 256)
            return ValidationResult.error("railroad.project.creation.author.error.length_long");

        if (text.length() < 3)
            return ValidationResult.warning("railroad.project.creation.author.error.length_short");

        return ValidationResult.ok();
    }

    /**
     * Checks that optional credits contain at most 256 characters.
     *
     * @param field text field containing the value to validate
     * @return the validation status and localized message, if any
     */
    public static ValidationResult validateCredits(TextField field) {
        if (field.getText().length() > 256)
            return ValidationResult.error("railroad.project.creation.credits.error.length_long");

        return ValidationResult.ok();
    }

    /**
     * Rejects descriptions longer than 2048 characters and warns for descriptions shorter than ten characters.
     *
     * @param area text area containing the project description
     * @return the validation status and localized message, if any
     */
    public static ValidationResult validateDescription(TextArea area) {
        String text = area.getText();
        if (text.length() > 2048)
            return ValidationResult.error("railroad.project.creation.description.error.length_long");

        if (text.length() < 10)
            return ValidationResult.warning("railroad.project.creation.description.error.length_short");

        return ValidationResult.ok();
    }

    /**
     * Checks an optional issue tracker URL and its length, including a GitHub issues-path check.
     * Performs a synchronous HTTP HEAD request without following redirects and warns unless the response is HTTP 200.
     *
     * @param field text field containing the value to validate
     * @return the validation status and localized message, if any
     */
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

    /**
     * Checks an optional update URL for length, URL syntax, and a {@code .json} suffix.
     * Performs a synchronous HTTP HEAD request without following redirects and warns unless the response is HTTP 200.
     *
     * @param field text field containing the value to validate
     * @return the validation status and localized message, if any
     */
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

    /**
     * Checks an optional URL using the supplied validation message namespace.
     * Performs a synchronous HTTP HEAD request without following redirects and warns unless the response is HTTP 200.
     *
     * @param field text field containing the value to validate
     * @param errorKey segment inserted after {@code railroad.project.creation.} in validation message keys
     * @return the validation status and localized message, if any
     */
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

    /**
     * Requires a nonblank group identifier of at most 256 characters containing only ASCII letters, digits, and dots.
     *
     * @param field text field containing the value to validate
     * @return the validation status and localized message, if any
     */
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

    /**
     * Requires a nonblank artifact identifier of at most 256 characters containing only lowercase ASCII letters,
     * digits, and hyphens.
     *
     * @param field text field containing the value to validate
     * @return the validation status and localized message, if any
     */
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

    /**
     * Requires a nonblank version of at most 256 characters containing only ASCII letters, digits, dots, and hyphens.
     *
     * @param field text field containing the value to validate
     * @return the validation status and localized message, if any
     */
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

    /**
     * Normalizes path text to backslash separators and applies whitespace and separator cleanup.
     *
     * @param path path text to normalize
     * @return the cleaned path text
     */
    public static String getRepairedPath(String path) {
        while (path.endsWith(" ")) {
            path = path.substring(0, path.length() - 1);
        }

        path = path.replace("/", "\\");

        // Remove trailing backslashes
        while (path.endsWith("\\")) {
            path = path.substring(0, path.length() - 1);
        }

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

    /**
     * Creates a boolean binding that mirrors a property.
     *
     * @param property boolean property to observe
     * @return a binding with the same boolean value as the property
     */
    public static BooleanBinding createBinding(BooleanProperty property) {
        return Bindings.when(property).then(true).otherwise(false);
    }

    /**
     * Removes whitespace, hyphens, and underscores while capitalizing the next character after each separator.
     *
     * @param projectName project display name, possibly {@code null} or blank
     * @return the derived class name, or an empty string for null or blank input
     */
    public static String projectNameToMainClass(String projectName) {
        if (projectName == null || projectName.isBlank())
            return "";

        var builder = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : projectName.toCharArray()) {
            if (Character.isWhitespace(c) || c == '-' || c == '_') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                builder.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                builder.append(c);
            }
        }

        return builder.toString();
    }

    /**
     * Derives a mod identifier using the same conversion as {@link #projectNameToArtifactId(String)}.
     * This conversion preserves hyphens and does not guarantee that mod ID validation succeeds.
     *
     * @param projectName project display name, possibly {@code null} or blank
     * @return the lowercase filtered identifier, or an empty string for null or blank input
     */
    public static String projectNameToModId(String projectName) {
        return projectNameToArtifactId(projectName);
    }

    /**
     * Lowercases a project name and removes characters other than ASCII letters, digits, and hyphens.
     *
     * @param projectName project display name, possibly {@code null} or blank
     * @return the derived artifact identifier, or an empty string for null or blank input
     */
    public static String projectNameToArtifactId(String projectName) {
        if (projectName == null || projectName.isBlank())
            return "";

        return projectName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\-]", "");
    }
}
