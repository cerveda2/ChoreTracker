plugins {
    id("choretracker.android.application.compose")
    id("choretracker.hilt")
    alias(libs.plugins.google.services)
}

android {
    namespace = "cz.dcervenka.choretracker"

    defaultConfig {
        applicationId = "cz.dcervenka.choretracker"
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    implementation(project(":core:design"))
    implementation(project(":core:data"))
    implementation(project(":core:data-contract"))
    implementation(project(":core:model"))
    implementation(project(":core:remote-firebase"))
    implementation(project(":core:sync"))
    implementation(project(":feature:auth:api"))
    implementation(project(":feature:onboarding:api"))
    implementation(project(":feature:dashboard:api"))
    implementation(project(":feature:household:api"))
    implementation(project(":feature:stats:api"))
    implementation(project(":feature:settings:api"))
    implementation(project(":feature:auth:impl"))
    implementation(project(":feature:onboarding:impl"))
    implementation(project(":feature:dashboard:impl"))
    implementation(project(":feature:household:impl"))
    implementation(project(":feature:stats:impl"))
    implementation(project(":feature:settings:impl"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
