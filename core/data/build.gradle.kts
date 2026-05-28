plugins {
    alias(libs.plugins.choretracker.android.library)
    alias(libs.plugins.choretracker.hilt)
}

android {
    namespace = "cz.dcervenka.choretracker.core.data"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.dataContract)
    implementation(projects.core.databaseRoom)
    implementation(libs.room.runtime)
    implementation(projects.core.remoteContract)
    implementation(projects.core.domain)
    implementation(libs.kotlinx.datetime)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.timber)

    testImplementation(projects.core.test)
    testImplementation(libs.google.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
