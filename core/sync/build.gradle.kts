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
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.room.runtime)
    implementation(libs.timber)

    testImplementation(projects.core.test)
    testImplementation(libs.junit4)
    testImplementation(libs.google.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.androidx.test.ext.junit)
}
