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

include(":app")
include(":core:common")
include(":core:model")
include(":core:domain")
include(":core:design")
include(":core:data-contract")
include(":core:database-room")
include(":core:remote-contract")
include(":core:remote-firebase")
include(":core:data")
include(":core:sync")
include(":core:testing")
include(":feature:auth:api")
include(":feature:auth:impl")
include(":feature:onboarding:api")
include(":feature:onboarding:impl")
include(":feature:dashboard:api")
include(":feature:dashboard:impl")
include(":feature:household:api")
include(":feature:household:impl")
include(":feature:stats:api")
include(":feature:stats:impl")
include(":feature:settings:api")
include(":feature:settings:impl")
