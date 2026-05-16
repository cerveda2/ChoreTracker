plugins {
    alias(libs.plugins.choretracker.android.library)
    alias(libs.plugins.choretracker.hilt)
}

android {
    namespace = "cz.dcervenka.choretracker.core.sync"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.dataContract)
    implementation(projects.core.databaseRoom)
    implementation(projects.core.model)
    implementation(projects.core.remoteContract)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.timber)

    testImplementation(projects.core.test)
    testImplementation(libs.junit4)
    testImplementation(libs.google.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
