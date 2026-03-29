plugins {
    id("choretracker.android.library")
    id("choretracker.android.library.compose")
}

android {
    namespace = "cz.dcervenka.choretracker.core.design"
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
