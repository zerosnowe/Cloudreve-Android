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

rootProject.name = "Cloudreve"
include(":app")
include(":core:common")
include(":core:domain")
include(":core:ui-miuix")
include(":core:network")
include(":core:data")
include(":core:database")
include(":core:security")
include(":core:transfer-worker")
include(":feature:auth")
include(":feature:files")
include(":feature:preview")
include(":feature:share")
include(":feature:settings")
