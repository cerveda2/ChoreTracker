plugins {
    id("choretracker.android.library")
    id("choretracker.hilt")
    id("choretracker.room")
}

android {
    namespace = "cz.dcervenka.choretracker.core.database"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(libs.kotlinx.datetime)
    testImplementation(libs.room.testing)
}
