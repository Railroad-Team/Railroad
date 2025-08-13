# Building

## Prerequisites

- [Git](https://git-scm.com/downloads)
- [Java 21](https://adoptium.net/temurin/releases/?package=jdk&version=21)
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
   - For `SDK`, select `Java 21 SDK` (may also appear only as `21`)
   - For `Language level`, select `SDK default`.
   - Then hit `Apply`
3. Go to `File > Settings > Build, Execution, Deployment > Build Tools > Gradle`, and set **Gradle JVM** to `Java 21 SDK` (may also appear only as `21`).
4. Open the **Gradle tab** (right sidebar) and click the _looping circular arrow icon_ to **Reload All Gradle Projects**.
   (The tooltip may say "Reload All Gradle Projects" or "Sync All Gradle Projects", depending on your IDE version.)

**Adding Lombok plugin**:

1. Go to `File > Settings > Plugins`
2. IntelliJ will usually suggest the **Lombok** plugin automatically.
   If not, search for `Lombok` manually and install it.

---

### Manual Setup (No IDE)

> [!TIP]
> If you're already using Java 21, no manual setup is needed.
> On Unix systems, you might need to give `gradlew` execute permissions:
>
> ```sh
> chmod +x ./gradlew
> ```
>
> Run this if you see a "Permission denied" error when trying to execute the wrapper.

1. Make sure you are running **Java 21**. Otherwise, build will fail.

   ```sh
   java --version
   ```

   Example output:

   ```sh
   $ java --version
   openjdk 21.0.7 2025-04-15
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

## Running

Run the `runShadow` task:

```sh
./gradlew runShadow
```

Or run the compiled JAR directly:

```sh
java -jar build/libs/railroad-<VERSION>-SNAPSHOT-all.jar
```
