plugins {
    id("choretracker.android.feature")
    id("choretracker.hilt")
}

android {
    namespace = "cz.dcervenka.choretracker.feature.stats.impl"
}

dependencies {
    implementation(project(":feature:stats:api"))
    implementation(project(":core:data-contract"))
    implementation(project(":core:design"))
    implementation(project(":core:domain"))
    implementation(project(":core:model"))
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
}
