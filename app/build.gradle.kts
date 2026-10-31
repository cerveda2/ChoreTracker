plugins {
    alias(libs.plugins.choretracker.android.application.compose)
    alias(libs.plugins.choretracker.hilt)
    alias(libs.plugins.choretracker.firebase)
    alias(libs.plugins.google.services)
}

android {
    namespace = "cz.dcervenka.choretracker"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "cz.dcervenka.choretracker"
    }

    signingConfigs {
        // Upload-key signing, configured outside the repo (e.g. ~/.gradle/gradle.properties, or -P
        // in CI). Without choretracker.release.storeFile the release build is simply left unsigned
        // (app-release-unsigned.apk) instead of failing or silently falling back to the debug key.
        val storeFilePath = providers.gradleProperty("choretracker.release.storeFile").orNull
        if (storeFilePath != null) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = providers.gradleProperty("choretracker.release.storePassword").get()
                keyAlias = providers.gradleProperty("choretracker.release.keyAlias").get()
                keyPassword = providers.gradleProperty("choretracker.release.keyPassword").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }
}

dependencies {
    implementation(projects.core.design)
    implementation(projects.core.data)
    implementation(projects.core.dataContract)
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.remoteFirebase)
    implementation(projects.core.sync)
    implementation(projects.core.notifications)
    implementation(projects.core.reminders)
    implementation(projects.feature.auth.impl)
    implementation(projects.feature.onboarding.impl)
    implementation(projects.feature.dashboard.impl)
    implementation(projects.feature.chores.impl)
    implementation(projects.feature.stats.impl)
    implementation(projects.feature.settings.impl)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(libs.timber)
    testImplementation(projects.core.common)
    testImplementation(projects.core.test)
    testImplementation(libs.junit4)
    testImplementation(libs.google.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
