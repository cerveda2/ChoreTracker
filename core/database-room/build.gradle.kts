plugins {
    alias(libs.plugins.choretracker.android.library)
    alias(libs.plugins.choretracker.hilt)
    alias(libs.plugins.choretracker.room)
}

android {
    namespace = "cz.dcervenka.choretracker.core.database"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(libs.kotlinx.datetime)
    testImplementation(libs.room.testing)
}
