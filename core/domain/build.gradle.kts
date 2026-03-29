plugins {
    alias(libs.plugins.choretracker.kotlin.library)
}

dependencies {
    implementation(projects.core.dataContract)
    implementation(projects.core.model)
    implementation(libs.javax.inject)
}
