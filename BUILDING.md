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

## Validating JavaFX CSS

Run `./gradlew validateJavafxCss` to parse every `.css` file under
`src/main/resources/assets/railroad/styles` with the application's JavaFX version.
The check runs in GitHub Actions and as part of `check`, independently of the formatting ratchet.
It runs without a display or application startup and fails on parser diagnostics, including invalid
JavaFX values and broken stylesheet imports. Diagnostics include the stylesheet URL and parser location.
The browser CSS used by the Javadoc report is intentionally outside this scope.

This is parser validation, not a complete runtime style check: unknown property names, unresolved
looked-up colors, selector matches, image availability, and control-specific conversions may require
applying styles to a live scene. Validator regression tests run with `./gradlew cssValidationTest`.

## Running the OpenJDK javac suite against SST

`sstJavacTest` runs headlessly against a local OpenJDK Git checkout. The default Railroad test suite has no OpenJDK
checkout dependency. Use a checkout outside Railroad and keep its working tree clean for reproducible results. The
recommended baseline is OpenJDK `jdk-25+36`, which matches Railroad's Java 25 toolchain. A sparse checkout is enough.

```sh
git clone --depth 1 --filter=blob:none --sparse --branch jdk-25+36 https://github.com/openjdk/jdk.git ../openjdk-25
git -C ../openjdk-25 sparse-checkout set test/langtools test/lib
./gradlew sstJavacTest -PopenJdkCheckout=../openjdk-25
```

On Windows use `./gradlew.bat`. Use `-PsstJavacFilter=path` to select a file or directory relative to
`test/langtools/tools/javac`. Append `#id` to select one description in a file, for example
`'-PsstJavacFilter=Example.java#id0'`. A file selection without an ID runs all its descriptions.

The harness discovers tagged test descriptions and runs `@compile`, `@run compile` and source-based `@build` actions
in order. It resolves explicitly named sources and build dependencies, including `@library` source roots. Each Java
input must round-trip through SST exactly and agree with the Java 25 javac parser on whether it has syntax errors.
Supported compile actions check SST errors against the expected compile outcome. Negative actions with `/ref`
compare mapped diagnostic categories and source locations from the reference file.

The harness reads source and reference files in place and never executes test programs or annotation processors.
Runtime assertions, bytecode checks, generated sources, module setups, other source levels, and unmapped reference
diagnostics receive an unsupported reason. A tagged Java driver can still be checked as on-disk parser input.
SST requests share a 30-second deadline per case. A separate worker JVM has a 45-second watchdog. The runner records
timeouts and analysis crashes as case errors, then replaces the worker when needed.

The console summary and `build/reports/sst-javac/results.json` record the checkout revision, toolchain, case IDs, and
results. A mismatch or harness error fails the Gradle task. Unsupported checks are reported separately, with any
completed syntax and compile checks counted in the same case record.

## Formatting code

Run the formatter before committing Java, CSS, Groovy, or Gradle script changes:

```sh
./gradlew format
```

Formatting is ratcheted from `HEAD` by default, so only changed and untracked files are touched.
CSS files under `src` use Prettier (requires Node.js and npm); Groovy files under `src` and Gradle scripts in the project root, `gradle`, and
`src` use Groovy-Eclipse. Both use four-space indentation, trim trailing whitespace, and end files with a newline.
The quality plugin supplies the CSS and Groovy settings.
The wide CSS print width keeps long selectors inline because JavaFX rejects line breaks before `>`.
Gradle templates under `templates` are excluded because they contain template directives.

For Java, this applies Railroad's syntax-aware style rules first, followed by the Eclipse JDT formatter.
In particular, a control-flow block whose
only statement is `return`, `throw`, `break`, `continue`, or `yield` is written without braces; every non-terminal
control-flow body uses braces. `else if` chains remain compact. An exact non-collection local construction such as
`Widget widget = new Widget()` uses `var`; declarations involving target-type inference, a different declared type,
anonymous classes, fields, or multiple variables retain their explicit type. Unused-variable conventions named
`ignored`, `$`, or `$` followed by digits use Java 25's unnamed variable `_` where the declaration context permits it;
the original name is retained if it is referenced or cannot legally be unnamed.
Package-qualified type references such as `java.util.List` use `List` with an import when the compiler can resolve the
type without name conflicts. This also covers constructors, annotations, class literals, method references, and static
member access; nested types retain their enclosing type (for example, `Map.Entry`). Existing imports are reused, and
types in `java.lang` or the current package need no added import. Unresolved or shadowed names and references containing
comments are retained. The Gradle tasks supply the project's compile classpath and source roots for type resolution.
Wrapped method and constructor parameter lists, and record component lists, start on the line after the opening
parenthesis, with one parameter or component per line. The closing parenthesis sits on its own line, aligned with the
declaration; single-line lists remain inline when they fit.

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

To intentionally reformat every supported source file instead of only current changes, pass the `formatAll` property:

```sh
./gradlew format -PformatAll
```

The [Railroad quality plugin](https://github.com/Railroad-Team/railroad-quality-plugin) owns the formatting profiles,
semantic rules, and their tests. The version is pinned in `build.gradle`.

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
and generated members (including Lombok methods and implicit record accessors) are excluded. Methods annotated with
`@Override` or `@java.lang.Override` are also excluded from checks and coverage totals, including their parameter and
return documentation. Unannotated methods are still checked; `{@inheritDoc}` on those methods is reported as unverified
because this source-only check does not resolve inheritance.
This checks documentation presence, not prose accuracy or full Javadoc validity.

To enforce complete coverage, run:

```sh
./gradlew javadocCoverageCheck
```

This generates the same report, then fails if any declarations are incomplete. The reporting task itself succeeds
with incomplete coverage, so it can be used while improving documentation. Neither task compiles the application or
requires its dependencies; invalid Java syntax fails report generation. `javadocCoverageCheck` is opt-in and is not
attached to `check`, since the existing documentation is not yet complete.

The quality plugin supplies the report generator, its tests, and the embedded HTML, CSS, and JavaScript assets.

## Running

Run the `runShadow` task:

```sh
./gradlew runShadow
```

Or run the compiled JAR directly:

```sh
java -jar build/libs/railroad-<VERSION>-SNAPSHOT-all.jar
```
