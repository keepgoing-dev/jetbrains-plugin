# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Documentation

When you add, remove, or change what the plugin shows or how it reads data, update the docs page using a Haiku sub-agent:

```
Agent({
  subagent_type: "haiku",
  model: "haiku",
  description: "Update JetBrains plugin docs page",
  prompt: "Update apps/web/src/pages/docs/jetbrains.astro to reflect: [describe what changed]. Explain what users see in the IDE and how it's read-only. No em dashes."
})
```

Docs page: `apps/web/src/pages/docs/jetbrains.astro`

## Context

This is a standalone Kotlin/Gradle IntelliJ Platform plugin inside the KeepGoing monorepo. It is a **read-only companion** - it never writes to `.keepgoing/`. See the root `CLAUDE.md` for overall monorepo architecture.

JDK 17 required. All commands run from `apps/jetbrains-plugin/`.

## Build Commands

```bash
./gradlew build              # Compile and assemble
./gradlew runIde             # Launch sandboxed IDE with the plugin installed (primary dev workflow)
./gradlew buildPlugin        # Produce distributable ZIP in build/distributions/
./gradlew runPluginVerifier  # Verify compatibility against IC-2023.2.8, IC-2024.3.7, IC-2025.1.7
```

Publishing (requires env vars `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD`, `PUBLISH_TOKEN`):
```bash
./gradlew signPlugin
./gradlew publishPlugin
```

## Architecture

The plugin has three layers:

**1. Data layer - `KeepGoingDataService` (project-level service)**
- Reads `.keepgoing/` on project open, watches for VFS changes (500ms debounce), and refreshes every 30s to catch external writes from MCP/CLI.
- Primary read path: SQLite via `ProjectDbReader` from `libs/kotlin-shared`.
- Fallback read path: Gson JSON parsing of `meta.json`, `state.json`, `sessions.json`, `decisions.json`. Gson fallback types are private inner classes inside `KeepGoingDataService`.
- Publishes `KeepGoingDataListener.TOPIC` on the project `MessageBus` when data changes. UI components subscribe to this to auto-refresh.

**2. Entry point - `KeepGoingStartupActivity`**
- Runs once on project open (`ProjectActivity`).
- If no `.keepgoing/meta.json` exists, shows a one-time "Get Started" setup notification (suppressed after dismissal via `PropertiesComponent`).
- If last activity is >= 3 days ago, shows a re-entry briefing notification with optional "Open last touched files" action.

**3. UI layer**
- `KeepGoingToolWindowPanel` / `KeepGoingToolWindowFactory`: Right-side tool window showing full briefing, session history, and decisions.
- `KeepGoingStatusBarWidget` / `KeepGoingStatusBarWidgetFactory`: Status bar indicator showing last activity time.
- All UI components subscribe to `KeepGoingDataListener.TOPIC` for reactive updates.

**Shared library - `libs/kotlin-shared`**
- Included via composite build (`includeBuild("../../libs/kotlin-shared")`).
- Provides `ProjectDbReader`, `BriefingGenerator`, `TimeUtils`, and shared model types (`SessionCheckpoint`, `ReEntryBriefing`, `ProjectMeta`, `ProjectState`, `DecisionRecord`).
- When updating shared model types, the Kotlin models here must stay in sync with TypeScript types in `packages/shared/src/types.ts`.

## Plugin Registration

All extension points are declared in `src/main/resources/META-INF/plugin.xml`:
- `postStartupActivity` - `KeepGoingStartupActivity`
- `projectService` - `KeepGoingDataService`
- `toolWindow` - anchored right, factory `KeepGoingToolWindowFactory`
- `statusBarWidgetFactory` - `KeepGoingStatusBarWidgetFactory`
- `notificationGroup` - `"KeepGoing Notifications"` (sticky balloon)

## Target Platform

- Kotlin 1.9.21, JVM toolchain 17
- IntelliJ Platform IC (Community) 2023.2
- Compatible range: since build 232, until build 252.*
