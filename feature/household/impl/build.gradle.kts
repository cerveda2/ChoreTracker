plugins {
    alias(libs.plugins.choretracker.android.feature)
    alias(libs.plugins.choretracker.hilt)
}

android {
    namespace = "cz.dcervenka.choretracker.feature.household.impl"
}

dependencies {
    implementation(projects.feature.household.api)
    implementation(projects.core.common)
    implementation(projects.core.dataContract)
    implementation(projects.core.design)
    implementation(projects.core.model)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
}
