plugins {
    alias(libs.plugins.choretracker.android.library)
}

android {
    namespace = "cz.dcervenka.choretracker.core.formatters"

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.junit4)
    testImplementation(libs.google.truth)
    testImplementation("org.robolectric:robolectric:4.16")
}
