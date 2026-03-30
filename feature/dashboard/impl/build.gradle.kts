plugins {
    alias(libs.plugins.choretracker.android.feature)
    alias(libs.plugins.choretracker.hilt)
}

android {
    namespace = "cz.dcervenka.choretracker.feature.dashboard.impl"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.design)
    implementation(projects.core.domain)
    implementation(projects.core.formatters)
    implementation(projects.core.model)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.datetime)

    testImplementation(projects.core.dataContract)
    testImplementation(projects.core.test)
    testImplementation(libs.junit4)
    testImplementation(libs.google.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
