plugins {
    alias(libs.plugins.choretracker.android.library)
    alias(libs.plugins.choretracker.hilt)
}

android {
    namespace = "cz.dcervenka.choretracker.core.sync"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.dataContract)
    implementation(projects.core.databaseRoom)
    implementation(projects.core.model)
    implementation(projects.core.remoteContract)
    implementation(libs.androidx.work.runtime.ktx)
}
