# Repository Guidelines

- **Pre-release policy:** No backward-compatibility guarantees until the first public release. Freely remove or change obsolete APIs.
- **Architecture:** Reuse existing systems; avoid duplicate abstractions, speculative infrastructure and unnecessary complexity.
- **Plugin development:** Design clean plugin-facing APIs, but don't introduce version negotiation or compatibility layers yet.
- **Code quality:** Prefer straightforward, maintainable Java code over elaborate frameworks.
- **Comments:** Avoid unnecessary comments. Add comments only where the code is unclear or a comment provides useful clarification; do not restate what the code already expresses. This restriction does not apply to Javadocs.
- **Testing:** Add regression tests for behavioural changes, especially concurrency, document state and lifecycle.
- **Scope control:** Don't refactor unrelated systems or expand tasks without a concrete reason.
- **AI autonomy:** Implement routine decisions independently, but escalate consequential architectural changes.

## Formatting

- Follow `.editorconfig` and the existing Spotless configuration in `build.gradle`. Java uses four-space indentation and a 120-character line-length target. Use UTF-8, remove trailing whitespace and end files with a newline; Java and CSS use LF line endings.
- Formatting during implementation is irrelevant as long as it is corrected before finishing. After changing supported source files, you must run `./gradlew format formatCheck` (Windows: `.\gradlew.bat format formatCheck`) after your final edits and resolve any failures before declaring the task complete. These tasks combine Railroad's semantic Java style rules with Spotless formatting for Java, CSS, Groovy and Gradle files.
- Treat `config/format/railroad-eclipse-formatter.xml`, `config/format/railroad-groovy.properties` and the configured CSS formatter as the formatting authorities. Let the existing tools apply their rules rather than introducing another formatter or manually imposing a competing style.
- Resolve reported semantic style violations, including explicit visibility requirements. Choose access levels deliberately; the formatter does not change them automatically.
- Keep the default formatting scope, which checks files changed relative to `HEAD`. Use `-PformatRatchetFrom=<ref>` when a different comparison base is needed, and reserve `-PformatAll` for explicitly requested repository-wide formatting. Review the resulting diff and avoid unrelated formatting changes.
