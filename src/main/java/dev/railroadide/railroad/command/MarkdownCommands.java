package dev.railroadide.railroad.command;

import dev.railroadide.railroad.ide.ui.MarkdownPreviewPane;
import dev.railroadide.railroad.ide.ui.MarkdownPreviewPane.MarkdownLayoutType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Existing Markdown toolbar actions, with stable identities for each formatting operation.
 */
public final class MarkdownCommands {
    /**
     * Prevents instantiation of command definitions.
     */
    private MarkdownCommands() {
    }

    /**
     * Selects code-only layout.
     */
    public static final Command<MarkdownPreviewPane> CODE = layout(MarkdownLayoutType.CODE);
    /**
     * Selects split code and preview layout.
     */
    public static final Command<MarkdownPreviewPane> SPLIT = layout(MarkdownLayoutType.SPLIT);
    /**
     * Selects preview-only layout.
     */
    public static final Command<MarkdownPreviewPane> PREVIEW = layout(MarkdownLayoutType.PREVIEW);
    /**
     * Opens the existing image-insertion dialog.
     */
    public static final Command<MarkdownPreviewPane> IMAGE = CommandRegistry.action(
        "railroad:markdown_image", "railroad.command.markdown.image", MarkdownPreviewPane.class,
        _ -> true, MarkdownPreviewPane::imageDialog);
    /**
     * Formatting commands indexed by their existing insertion prefix.
     */
    private static final Map<String, Command<MarkdownPreviewPane>> INSERTIONS = createInsertions();
    /**
     * Heading commands ordered from level one through level six.
     */
    private static final List<Command<MarkdownPreviewPane>> HEADINGS = IntStream.rangeClosed(1, 6)
        .mapToObj(level -> CommandRegistry.action("railroad:markdown_heading_" + level,
            "railroad.command.markdown.heading_" + level, MarkdownPreviewPane.class, _ -> true,
            p -> p.insertMarkdown("#".repeat(level), null)))
        .toList();

    /**
     * Returns the existing formatting action for a toolbar prefix.
     *
     * @param prefix Markdown text inserted before the caret
     * @return corresponding formatting command
     */
    public static Command<MarkdownPreviewPane> insertion(String prefix) {
        return INSERTIONS.get(prefix);
    }

    /**
     * Returns a heading insertion command.
     *
     * @param level heading level from one through six
     * @return corresponding heading command
     */
    public static Command<MarkdownPreviewPane> heading(int level) {
        return HEADINGS.get(level - 1);
    }

    /**
     * Registers one layout action.
     *
     * @param layout layout requested by the existing toolbar
     * @return registered layout command
     */
    private static Command<MarkdownPreviewPane> layout(MarkdownLayoutType layout) {
        String id = layout.name().toLowerCase(Locale.ROOT);
        return CommandRegistry.action("railroad:markdown_layout_" + id,
            "railroad.command.markdown.layout_" + id, MarkdownPreviewPane.class, _ -> true,
            p -> p.applyLayout(layout));
    }

    /**
     * Registers the existing insertion syntax with stable action names.
     *
     * @return insertion commands indexed by prefix
     */
    private static Map<String, Command<MarkdownPreviewPane>> createInsertions() {
        Map<String, Command<MarkdownPreviewPane>> commands = new LinkedHashMap<>();
        commands.put("**", CommandRegistry.action("railroad:markdown_bold",
            "railroad.command.markdown.bold", MarkdownPreviewPane.class, _ -> true,
            p -> p.insertMarkdown("**", "**")));
        commands.put("_", CommandRegistry.action("railroad:markdown_italic",
            "railroad.command.markdown.italic", MarkdownPreviewPane.class, _ -> true,
            p -> p.insertMarkdown("_", "_")));
        commands.put("> ", CommandRegistry.action("railroad:markdown_quote",
            "railroad.command.markdown.quote", MarkdownPreviewPane.class, _ -> true,
            p -> p.insertMarkdown("> ", null)));
        commands.put("`", CommandRegistry.action("railroad:markdown_code",
            "railroad.command.markdown.code", MarkdownPreviewPane.class, _ -> true,
            p -> p.insertMarkdown("`", "`")));
        commands.put("[", CommandRegistry.action("railroad:markdown_link",
            "railroad.command.markdown.link", MarkdownPreviewPane.class, _ -> true,
            p -> p.insertMarkdown("[", "](url)")));
        commands.put("- ", CommandRegistry.action("railroad:markdown_unordered_list",
            "railroad.command.markdown.unordered_list", MarkdownPreviewPane.class, _ -> true,
            p -> p.insertMarkdown("- ", null)));
        commands.put("1. ", CommandRegistry.action("railroad:markdown_ordered_list",
            "railroad.command.markdown.ordered_list", MarkdownPreviewPane.class, _ -> true,
            p -> p.insertMarkdown("1. ", null)));
        commands.put("- [ ]", CommandRegistry.action("railroad:markdown_task_list",
            "railroad.command.markdown.task_list", MarkdownPreviewPane.class, _ -> true,
            p -> p.insertMarkdown("- [ ]", null)));
        commands.put("---", CommandRegistry.action("railroad:markdown_horizontal_rule",
            "railroad.command.markdown.horizontal_rule", MarkdownPreviewPane.class, _ -> true,
            p -> p.insertMarkdown("---", null)));
        commands.put("~~", CommandRegistry.action("railroad:markdown_strikethrough",
            "railroad.command.markdown.strikethrough", MarkdownPreviewPane.class, _ -> true,
            p -> p.insertMarkdown("~~", "~~")));
        commands.put("```", CommandRegistry.action("railroad:markdown_code_block",
            "railroad.command.markdown.code_block", MarkdownPreviewPane.class, _ -> true,
            p -> p.insertMarkdown("```", "```")));
        return Map.copyOf(commands);
    }

    /**
     * Initializes the existing Markdown action definitions.
     */
    public static void initialize() {
    }
}
