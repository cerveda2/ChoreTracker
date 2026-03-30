plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

group = "cz.dcervenka.choretracker.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.ksp.gradle.plugin)
    compileOnly(libs.hilt.gradle.plugin)
    compileOnly(libs.detekt.gradle.plugin)
    compileOnly(libs.detekt.formatting)
}

tasks {
    validatePlugins {
        enableStricterValidation = true
        failOnWarning = true
    }
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "choretracker.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidApplicationCompose") {
            id = "choretracker.android.application.compose"
            implementationClass = "AndroidApplicationComposeConventionPlugin"
        }
        register("androidLibrary") {
            id = "choretracker.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "choretracker.android.library.compose"
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "choretracker.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("hilt") {
            id = "choretracker.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("room") {
            id = "choretracker.room"
            implementationClass = "RoomConventionPlugin"
        }
        register("kotlinLibrary") {
            id = "choretracker.kotlin.library"
            implementationClass = "KotlinLibraryConventionPlugin"
        }
        register("detekt") {
            id = "choretracker.detekt"
            implementationClass = "DetektConventionPlugin"
        }
    }
}
