plugins {
    alias(libs.plugins.choretracker.android.feature)
    alias(libs.plugins.choretracker.hilt)
}

android {
    namespace = "cz.dcervenka.choretracker.feature.stats.impl"
}

dependencies {
    implementation(projects.feature.stats.api)
    implementation(projects.core.dataContract)
    implementation(projects.core.design)
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
}
