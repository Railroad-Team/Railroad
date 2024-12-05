# Building

## Prerequisites

- [Java 21](https://adoptium.net/temurin/releases/?package=jdk&version=21)
- [Git](https://git-scm.com/downloads)
- [Gradle](https://gradle.org/install/)
- [IntelliJ IDEA](https://www.jetbrains.com/idea/download/)
- [Lombok](https://projectlombok.org/setup/intellij)

## Setup

1. Clone the repository
    ```shell
    ./git clone https://github.com/Railroad-Team/Railroad.git
    ```
2. (Optional) Switch to the development branch
    ```shell
    ./git checkout dev
    ```
    ```shell
    ./git fetch
    ```
    ```shell
    ./git pull
    ```
3. Open the `build.gradle` file in IntelliJ IDEA
4. Go to `File` -> `Project Structure` -> `Project` -> `Project SDK` and select the Java 21 SDK
5. Go to `File` -> `Project Structure` -> `Project` -> `Project language level` and select `SDK default`
6. Go to `File` -> `Settings` -> `Build, Execution, Deployment` -> `Build Tools` -> `Gradle` and set the `Gradle JVM` to the Java 21 SDK
7. Go to the `Gradle` tab on the right side of the screen and click on the `Reload All Gradle Projects` button

## Building
1. Run the `shadowJar` task
    ```shell
    ./gradlew shadowJar
    ```
2. The compiled JAR file will be located in the `build/libs` directory
3. Run the JAR file
    ```shell
    java -jar build/libs/railroad-1.0-SNAPSHOT-all.jar
    ```

## Running
1. Run the `run` task
    ```shell
    ./gradlew runShadow
    ```

