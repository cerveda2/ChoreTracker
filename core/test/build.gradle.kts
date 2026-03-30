plugins {
    alias(libs.plugins.choretracker.kotlin.library)
}

dependencies {
    implementation(projects.core.model)
    implementation(libs.junit4)
    implementation(libs.kotlinx.coroutines.test)
}
