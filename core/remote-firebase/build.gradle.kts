plugins {
    id("choretracker.android.library")
    id("choretracker.hilt")
}

android {
    namespace = "cz.dcervenka.choretracker.core.remote.firebase"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        val useEmulators = providers.gradleProperty("choretracker.firebase.useEmulators")
            .orNull
            ?.toBooleanStrictOrNull()
            ?: false
        val authHost = providers.gradleProperty("choretracker.firebase.authEmulatorHost").orNull ?: "10.0.2.2"
        val authPort = providers.gradleProperty("choretracker.firebase.authEmulatorPort").orNull?.toIntOrNull() ?: 9099
        val firestoreHost = providers.gradleProperty("choretracker.firebase.firestoreEmulatorHost").orNull ?: "10.0.2.2"
        val firestorePort = providers.gradleProperty("choretracker.firebase.firestoreEmulatorPort").orNull?.toIntOrNull() ?: 8080

        buildConfigField("boolean", "USE_FIREBASE_EMULATORS", useEmulators.toString())
        buildConfigField("String", "FIREBASE_AUTH_EMULATOR_HOST", "\"$authHost\"")
        buildConfigField("int", "FIREBASE_AUTH_EMULATOR_PORT", authPort.toString())
        buildConfigField("String", "FIREBASE_FIRESTORE_EMULATOR_HOST", "\"$firestoreHost\"")
        buildConfigField("int", "FIREBASE_FIRESTORE_EMULATOR_PORT", firestorePort.toString())
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:remote-contract"))
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
}
