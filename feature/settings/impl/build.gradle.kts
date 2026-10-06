plugins {
    alias(libs.plugins.choretracker.android.feature)
    alias(libs.plugins.choretracker.hilt)
}

android {
    namespace = "cz.dcervenka.choretracker.feature.settings.impl"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.design)
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.qrose)

    testImplementation(projects.core.dataContract)
    testImplementation(projects.core.test)
}
