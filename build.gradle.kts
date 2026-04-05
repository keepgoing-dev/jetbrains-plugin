plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.21"
    id("org.jetbrains.intellij") version "1.16.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
}

intellij {
    pluginName.set(providers.gradleProperty("pluginName").get())
    version.set(providers.gradleProperty("platformVersion").get())
    type.set(providers.gradleProperty("platformType").get())
}

kotlin {
    jvmToolchain(providers.gradleProperty("javaVersion").get().toInt())
}

tasks {
    patchPluginXml {
        sinceBuild.set("232")
        untilBuild.set("252.*")
        changeNotes.set(provider {
            file("CHANGELOG.md").let { f ->
                if (f.exists()) {
                    val lines = f.readLines()
                    val start = lines.indexOfFirst { it.startsWith("## ") }
                    if (start >= 0) {
                        val end = lines.drop(start + 1).indexOfFirst { it.startsWith("## ") }
                        val section = if (end >= 0) lines.subList(start + 1, start + 1 + end) else lines.drop(start + 1)
                        "<ul>" + section.filter { it.startsWith("- ") }.joinToString("") { "<li>${it.removePrefix("- ")}</li>" } + "</ul>"
                    } else ""
                } else ""
            }
        })
    }

    runPluginVerifier {
        ideVersions.set(listOf("IC-2023.2.8", "IC-2024.3.7", "IC-2025.1.7"))
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN") ?: "")
        privateKey.set(System.getenv("PRIVATE_KEY") ?: "")
        password.set(System.getenv("PRIVATE_KEY_PASSWORD") ?: "")
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN") ?: "")
        channels.set(listOf(System.getenv("PUBLISH_CHANNEL") ?: "default"))
    }
}
