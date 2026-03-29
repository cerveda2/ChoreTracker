plugins {
    id("choretracker.android.library")
    id("choretracker.hilt")
}

android {
    namespace = "cz.dcervenka.choretracker.core.sync"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:data-contract"))
    implementation(libs.androidx.work.runtime.ktx)
}
