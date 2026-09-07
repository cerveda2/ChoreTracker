plugins {
    alias(libs.plugins.choretracker.android.library)
    alias(libs.plugins.choretracker.hilt)
}

android {
    namespace = "cz.dcervenka.choretracker.core.remote.firebase"

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        // FirebaseFirestoreException.Code's static initializer touches android.util.SparseArray,
        // which the default Android unit-test stub jar throws on ("not mocked") rather than
        // returning a usable value - this makes it return a harmless default instead, since
        // nothing here depends on that lookup table's actual contents.
        unitTests.isReturnDefaultValues = true
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

    buildTypes {
        release {
            // USE_FIREBASE_EMULATORS above is driven by a Gradle property meant for local dev
            // only - it applies to every build type via defaultConfig, so a release build
            // compiled in an environment that happens to have that property set (e.g. a
            // developer's local gradle.properties) would silently try to talk to a
            // loopback-only emulator instead of real Firebase. Hardcode it off for release so
            // that can't happen regardless of the environment it's built in.
            buildConfigField("boolean", "USE_FIREBASE_EMULATORS", "false")
        }
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.remoteContract)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.timber)

    testImplementation(libs.junit4)
    testImplementation(libs.google.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
