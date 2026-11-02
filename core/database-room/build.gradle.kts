plugins {
    alias(libs.plugins.choretracker.android.library)
    alias(libs.plugins.choretracker.hilt)
    alias(libs.plugins.choretracker.room)
}

android {
    namespace = "cz.dcervenka.choretracker.core.database"

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    sourceSets.getByName("test").assets.directories.add("$projectDir/schemas")
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.room.testing)
    testImplementation(libs.junit4)
    testImplementation(libs.google.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
