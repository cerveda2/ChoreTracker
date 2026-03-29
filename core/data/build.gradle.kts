plugins {
    id("choretracker.android.library")
    id("choretracker.hilt")
}

android {
    namespace = "cz.dcervenka.choretracker.core.data"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:data-contract"))
    implementation(project(":core:database-room"))
    implementation(project(":core:remote-contract"))
    implementation(project(":core:domain"))
    implementation(libs.kotlinx.datetime)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.ktx)
}
