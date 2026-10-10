# Wildfront

A Minecraft Java Edition mod (Fabric) about dynamic exploration: rare world events, mysterious
discoveries and abandoned places that make the world feel unpredictable and alive, without
replacing vanilla survival.

**Status:** Milestone 1 — project setup. The mod loads and logs its startup; no gameplay yet.

## Versions

| Component     | Version       |
|---------------|---------------|
| Minecraft     | 26.3          |
| Fabric Loader | 0.19.5        |
| Fabric API    | 0.162.0+26.3  |
| Fabric Loom   | 1.18.4        |
| Gradle        | 9.7.1 (wrapper) |
| Java          | JDK 25        |

Versions live in [gradle.properties](gradle.properties).

## Setup

1. Install JDK 25:

   ```
   winget install EclipseAdoptium.Temurin.25.JDK
   ```

2. Make sure `JAVA_HOME` points at the JDK 25 install directory and open a new terminal.
   `java -version` must report 25.
3. Gradle does not need to be installed; the wrapper (`gradlew.bat`) downloads it.

The first build downloads Gradle, Minecraft and all dependencies, which takes several minutes.

## Build

```
.\gradlew.bat build
```

Runs the unit tests and writes the mod jar to `build/libs/wildfront-<version>.jar`.

To run only the tests:

```
.\gradlew.bat test
```

## Development client

```
.\gradlew.bat runClient
```

Starts Minecraft 26.3 with Wildfront loaded. The game directory is `run/`.
To confirm the mod loaded, look for these lines in the console or in `run/logs/latest.log`:

```
[Wildfront] Initialized (version 0.1.0, environment CLIENT)
[Wildfront] Client initialized
```

A dedicated development server is available with `.\gradlew.bat runServer`
(accept the EULA in `run/eula.txt` on first start).

## Project layout

```
src/main/java/dev/wildfront/         common (server-authoritative) code, mod entrypoint
src/main/resources/                  fabric.mod.json, data and assets
src/client/java/dev/wildfront/client client-only code
src/test/java/                       JUnit tests
```

## License

MIT, see [LICENSE](LICENSE).
