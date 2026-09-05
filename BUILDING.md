# Building

## Prerequisites

- [Git](https://git-scm.com/downloads)
- [Java 25](https://adoptium.net/temurin/releases/?package=jdk&version=25)
- [Gradle](https://gradle.org/install/) or use embedded wrapper

**Recommended**:

- [IntelliJ IDEA](https://www.jetbrains.com/idea/download/)
- [Lombok](https://projectlombok.org/setup)

## Setup

1. Clone the repository.

   ```sh
   git clone https://github.com/Railroad-Team/Railroad.git
   cd Railroad
   ```

2. (Optional) Inside the project, switch to the development branch.

   If the branch doesn't exist locally yet, run `git fetch` first.

   ```sh
   git checkout dev
   git pull
   ```

---

### Using IntelliJ IDEA (Recommended)

1. Open the `build.gradle` file in _IntelliJ IDEA_.
2. Go to `File > Project Structure > Project`
    - For `SDK`, select `Java 25 SDK` (may also appear only as `25`)
    - For `Language level`, select `SDK default`.
    - Then hit `Apply`
3. Go to `File > Settings > Build, Execution, Deployment > Build Tools > Gradle`, and set **Gradle JVM** to
   `Java 25 SDK` (may also appear only as `25`).
4. Open the **Gradle tab** (right sidebar) and click the _looping circular arrow icon_ to **Reload All Gradle Projects
   **.
   (The tooltip may say "Reload All Gradle Projects" or "Sync All Gradle Projects", depending on your IDE version.)

**Adding Lombok plugin**:

1. Go to `File > Settings > Plugins`
2. IntelliJ will usually suggest the **Lombok** plugin automatically.
   If not, search for `Lombok` manually and install it.

---

### Manual Setup (No IDE)

> [!TIP]
> If you're already using Java 25, no manual setup is needed.
> On Unix systems, you might need to give `gradlew` execute permissions:
>
> ```sh
> chmod +x ./gradlew
> ```
>
> Run this if you see a "Permission denied" error when trying to execute the wrapper.

1. Make sure you are running **Java 25**. Otherwise, build will fail.

   ```sh
   java --version
   ```

   Example output:

   ```sh
   $ java --version
   openjdk 25.0.1 2025-10-21
   ```

2. (Optional) Generate Gradle wrapper.

   Use only if the wrapper is missing or corrupted.

   ```sh
   gradle wrapper
   ```

   This will generate all the `gradlew` scripts you'll need.

## Building a jar

Run the `shadowJar` task.

```sh
./gradlew shadowJar
```

The compiled JAR will be available in `build/libs/`.

## Formatting Java code

Run the formatter before committing Java changes:

```sh
./gradlew format
```

This applies Railroad's syntax-aware style rules first, followed by the Eclipse JDT formatter. By default, formatting is
ratcheted from `HEAD`, so only changed and untracked Java files are touched. In particular, a control-flow block whose
only statement is `return`, `throw`, `break`, `continue`, or `yield` is written without braces; every non-terminal
control-flow body uses braces. `else if` chains remain compact. An exact local construction such as
`Widget widget = new Widget()` uses `var`; declarations involving target-type inference, a different declared type,
anonymous classes, fields, or multiple variables retain their explicit type. Unused-variable conventions named
`ignored`, `$`, or `$` followed by digits use Java 25's unnamed variable `_` where the declaration context permits it;
the original name is retained if it is referenced or cannot legally be unnamed.
Package-qualified type references such as `java.util.List` use `List` with an import when the compiler can resolve the
type without name conflicts. This also covers constructors, annotations, class literals, method references, and static
member access; nested types retain their enclosing type (for example, `Map.Entry`). Existing imports are reused, and
types in `java.lang` or the current package need no added import. Unresolved or shadowed names and references containing
comments are retained. The Gradle tasks supply the project's compile classpath and source roots for type resolution.
Wrapped method and constructor parameter lists start on the line after the opening parenthesis, with one parameter per
line. The closing parenthesis sits on its own line, aligned with the declaration; single-line parameter lists remain
inline when they fit.

Package-private types, fields, methods, and constructors are rejected: choose `public`, `protected`, or `private`
explicitly where legal. `formatCheck` reports these declarations and fails until they are resolved. `format` reports
them but completes all formatting, including with `-PformatAll`, without failing or changing access levels. Implicitly
public interface members,
implicitly private enum constructors, enum constants, record components, and local/anonymous class declarations are
exempt. This rule follows the same changed-file ratchet as the other formatting checks.

To check formatting without modifying files, run:

```sh
./gradlew formatCheck
```

To intentionally reformat every Java source file instead of only current changes, pass the `formatAll` property:

```sh
./gradlew format -PformatAll
```

The canonical whitespace and wrapping settings are stored in
`config/format/railroad-eclipse-formatter.xml`. Structural rules and their tests live under `src/formatter/java` and
`src/test/java/dev/railroadide/railroad/formatter`, respectively.

## Javadoc coverage

Generate a searchable, self-contained HTML report for the public API in `sourceSets.main.allJava`:

```sh
./gradlew javadocCoverage
```

Open `build/reports/javadoc-coverage/index.html`. The report includes overall, package, and class coverage,
expandable package → class → member navigation, search, an incomplete-only filter, and source file/line locations.
Each declaration counts as complete only when all its documentation requirements pass.

Coverage requires a nonempty Javadoc description on public types, explicitly declared public methods and constructors,
and public `static final` fields (including interface constants and enum constants). Every method/constructor value
and type parameter needs a nonempty `@param` tag; non-void methods also need a nonempty `@return` tag (block or inline).
Type parameters and record components need `@param` tags on the type. Java 25 Markdown Javadocs are supported.

Public interfaces, enums, records, annotation types, and publicly accessible nested types are included. Private,
protected, and package-private declarations, members inside inaccessible types, test/tool sources, inherited members,
and generated members (including Lombok methods and implicit record accessors) are excluded. Overrides require their
own documentation; `{@inheritDoc}` is reported as unverified because this source-only check does not resolve inheritance.
This checks documentation presence, not prose accuracy or full Javadoc validity.

To enforce complete coverage, run:

```sh
./gradlew javadocCoverageCheck
```

This generates the same report, then fails if any declarations are incomplete. The reporting task itself succeeds
with incomplete coverage, so it can be used while improving documentation. Neither task compiles the application or
requires its dependencies; invalid Java syntax fails report generation. `javadocCoverageCheck` is opt-in and is not
attached to `check`, since the existing documentation is not yet complete.

Run the coverage tool's focused tests with `./gradlew javadocCoverageTest` (also included in `check`). The implementation,
report assets, and tests live in `src/javadocCoverageTool` and `src/javadocCoverageTest`; Gradle wiring is in
`gradle/javadoc-coverage.gradle`. Both Java source sets are included in `format` and `formatCheck`, including the
semantic style rules and Spotless checks.

The HTML layout lives in `src/javadocCoverageTool/resources/dev/railroadide/railroad/docs/report.ftlh`, rendered with
FreeMarker and automatic HTML escaping. Edit that template to change the report structure; `report.css` and `report.js`
are embedded verbatim so the output remains a single self-contained HTML file. FreeMarker is a coverage-tool dependency
and is not added to the application.

## Running

Run the `runShadow` task:

```sh
./gradlew runShadow
```

Or run the compiled JAR directly:

```sh
java -jar build/libs/railroad-<VERSION>-SNAPSHOT-all.jar
```
