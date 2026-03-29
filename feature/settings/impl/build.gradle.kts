plugins {
    id("choretracker.android.feature")
    id("choretracker.hilt")
}

android {
    namespace = "cz.dcervenka.choretracker.feature.settings.impl"
}

dependencies {
    implementation(project(":feature:settings:api"))
    implementation(project(":core:common"))
    implementation(project(":core:data-contract"))
    implementation(project(":core:design"))
    implementation(project(":core:model"))
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
}
