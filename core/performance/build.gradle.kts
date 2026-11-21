plugins {
    alias(libs.plugins.choretracker.android.library)
    alias(libs.plugins.choretracker.hilt)
}

android {
    namespace = "cz.dcervenka.choretracker.core.performance"
}

dependencies {
    implementation(projects.core.common)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.perf)
    implementation(libs.timber)
}
