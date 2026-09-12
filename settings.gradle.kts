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
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Sona"

include(":app")
include(":core:common")
include(":core:model")
include(":core:database")
include(":core:data")
include(":core:designsystem")
include(":core:navigation")
include(":core:datastore")
include(":feature:scanner")
include(":feature:playback")
include(":feature:library")
include(":feature:player")
include(":feature:settings")
include(":feature:equalizer")
