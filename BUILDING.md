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

## Running

Run the `runShadow` task:

```sh
./gradlew runShadow
```

Or run the compiled JAR directly:

```sh
java -jar build/libs/railroad-<VERSION>-SNAPSHOT-all.jar
```
