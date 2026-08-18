plugins {
    alias(libs.plugins.choretracker.android.library)
    alias(libs.plugins.choretracker.hilt)
}

android {
    namespace = "cz.dcervenka.choretracker.core.reminders"

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.dataContract)
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.timber)
    implementation(libs.kotlinx.datetime)

    testImplementation(projects.core.test)
    testImplementation(libs.kotlinx.datetime)
    testImplementation(libs.junit4)
    testImplementation(libs.google.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.androidx.test.ext.junit)
}
