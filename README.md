# KeepGoing JetBrains Plugin

A minimal IntelliJ Platform plugin that surfaces a **re-entry briefing** when you return to a project after a period of inactivity (default: 3+ days).

## How It Works

1. **Opt-in only**: The plugin activates only for projects that contain a `.keepgoing/meta.json` file. If the file is absent, nothing happens.
2. On project open, it reads `.keepgoing/state.json` and `.keepgoing/sessions.json` to determine when you last worked on the project.
3. If the inactivity threshold is met (>= 3 days), a notification balloon appears with:
   - **Last worked**: A human-readable time string (e.g. "5 days ago").
   - **Project intent**: The high-level goal, if recorded.
   - **Next step**: The suggested next action from your last session.
   - **Open last touched files**: A button that opens the files you last edited, using IntelliJ's `FileEditorManager`.

## Prerequisites

- **JDK 17** or later
- **IntelliJ IDEA** (Community or Ultimate, 2023.2+)

## Running & Debugging

### Run the plugin in a sandboxed IDE

From the `apps/jetbrains-plugin` directory:

```bash
# Using the Gradle wrapper (recommended once generated)
./gradlew runIde

# Or if you have Gradle installed globally
gradle runIde
```

This launches a sandboxed instance of IntelliJ IDEA with the plugin installed. Open a project that contains a `.keepgoing/` directory to test the notification.

### Generate the Gradle wrapper

If the wrapper scripts are not present, generate them:

```bash
gradle wrapper
```

### Build the plugin distribution

```bash
./gradlew buildPlugin
```

The resulting ZIP is placed in `build/distributions/`.

### Run verification checks

```bash
./gradlew runPluginVerifier
```

## Testing Locally

1. Create a test project directory with a `.keepgoing/` folder containing:

   **`.keepgoing/meta.json`**:
   ```json
   {
     "projectId": "test-project",
     "createdAt": "2025-01-01T00:00:00Z",
     "lastUpdated": "2025-01-01T00:00:00Z"
   }
   ```

   **`.keepgoing/state.json`**:
   ```json
   {
     "lastActivityAt": "2025-01-01T00:00:00Z"
   }
   ```

   **`.keepgoing/sessions.json`**:
   ```json
   {
     "version": 1,
     "project": "test-project",
     "sessions": [
       {
         "id": "s1",
         "timestamp": "2025-01-01T00:00:00Z",
         "summary": "Set up initial project",
         "nextStep": "Add unit tests",
         "touchedFiles": ["src/main.kt", "build.gradle.kts"],
         "workspaceRoot": "."
       }
     ]
   }
   ```

2. Run `./gradlew runIde` and open the test project in the sandboxed IDE.
3. A notification should appear with the re-entry briefing.

## Target IntelliJ Version

- **Since build**: 232 (IntelliJ 2023.2)
- **Until build**: 251.* (IntelliJ 2025.1.x)

## Project Structure

```
apps/jetbrains-plugin/
├── build.gradle.kts              # Gradle build with IntelliJ Plugin configuration
├── gradle.properties             # Plugin and platform versions
├── settings.gradle.kts           # Gradle settings
├── README.md                     # This file
└── src/main/
    ├── kotlin/com/keepgoing/plugin/
    │   └── KeepGoingStartupActivity.kt   # Main plugin logic
    └── resources/META-INF/
        └── plugin.xml            # Plugin descriptor
```
