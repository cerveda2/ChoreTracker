plugins {
    id("choretracker.android.library")
    id("choretracker.hilt")
}

android {
    namespace = "cz.dcervenka.choretracker.core.remote.firebase"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:remote-contract"))
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
}
