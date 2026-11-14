pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "gc_watch"
include(":core:model")
include(":core:communication")
include(":mobile")
include(":wear")

// ── Vendored BRouter routing engine (MIT, see third_party/brouter/README.md) ──
for (m in listOf("util", "codec", "expressions", "mapaccess", "core")) {
    include(":brouter-$m")
    project(":brouter-$m").projectDir = file("third_party/brouter/brouter-$m")
}
