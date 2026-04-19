# KeepGoing JetBrains Plugin

An IntelliJ Platform plugin that surfaces momentum context when you return to a project after a period of inactivity (default: 3+ days).

## Features

- **Re-entry briefing**: Notification balloon on project open when you've been away 3+ days, with focus, last worked time, and next step.
- **Sidebar panel**: Tool window (right side) showing the full briefing, session history, and decisions.
- **Status bar indicator**: Quick last-activity timestamp in the status bar.
- **Open last touched files**: Restore your workspace with one click from the notification.
- **Read-only**: Never writes to `.keepgoing/` - works alongside the MCP server and CLI.

## How It Works

1. **Opt-in only**: The plugin activates only for projects that contain a `.keepgoing/` directory. If absent, a one-time "Get Started" prompt appears.
2. On project open, reads `.keepgoing/` data - SQLite DB first, JSON files as fallback.
3. If the inactivity threshold is met (>= 3 days), a notification balloon appears.
4. The sidebar panel and status bar widget stay live, refreshing on file changes and every 30 seconds.

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
- **Until build**: 252.* (IntelliJ 2025.2.x)

## Project Structure

```
apps/jetbrains-plugin/
├── build.gradle.kts              # Gradle build with IntelliJ Plugin configuration
├── gradle.properties             # Plugin and platform versions
├── settings.gradle.kts           # Composite build includes libs/kotlin-shared
└── src/main/
    ├── kotlin/com/keepgoing/plugin/
    │   ├── KeepGoingStartupActivity.kt   # Entry point: re-entry notification on project open
    │   ├── data/
    │   │   └── KeepGoingDataService.kt   # Project service: reads/caches/watches .keepgoing/ data
    │   └── ui/
    │       ├── KeepGoingToolWindowFactory.kt  # Sidebar panel factory
    │       ├── KeepGoingToolWindowPanel.kt    # Sidebar panel content
    │       └── KeepGoingStatusBarWidget.kt    # Status bar indicator
    └── resources/META-INF/
        └── plugin.xml            # Plugin descriptor and extension point registrations
```
