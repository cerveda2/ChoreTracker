plugins {
    alias(libs.plugins.choretracker.android.library)
    alias(libs.plugins.choretracker.android.library.compose)
}

android {
    namespace = "cz.dcervenka.choretracker.core.design"
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
