plugins {
    alias(libs.plugins.choretracker.kotlin.library)
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.model)
}
