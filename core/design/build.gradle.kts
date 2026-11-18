plugins {
    alias(libs.plugins.choretracker.android.library)
    alias(libs.plugins.choretracker.android.library.compose)
}

android {
    namespace = "cz.dcervenka.choretracker.core.design"
}

dependencies {
    implementation(projects.core.common)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(projects.core.model)
    implementation(projects.core.formatters)
    implementation(libs.kotlinx.datetime)
}
