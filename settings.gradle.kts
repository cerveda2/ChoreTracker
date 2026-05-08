pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
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

rootProject.name = "ChoreTracker"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":core:common")
include(":core:model")
include(":core:domain")
include(":core:design")
include(":core:formatters")
include(":core:test")
include(":core:data-contract")
include(":core:database-room")
include(":core:remote-contract")
include(":core:remote-firebase")
include(":core:data")
include(":core:sync")
include(":feature:auth:impl")
include(":feature:onboarding:impl")
include(":feature:dashboard:impl")
include(":feature:stats:impl")
include(":feature:settings:impl")
