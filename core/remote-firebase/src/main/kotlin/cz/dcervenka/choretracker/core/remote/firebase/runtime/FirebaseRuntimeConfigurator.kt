package cz.dcervenka.choretracker.core.remote.firebase.runtime

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.functions.FirebaseFunctions
import cz.dcervenka.choretracker.core.remote.firebase.BuildConfig

// The deleteAccount callable is pinned to europe-west1 (see functions/src/index.ts); the client
// must request the same region or the call resolves to us-central1 and 404s.
internal const val FIREBASE_FUNCTIONS_REGION = "europe-west1"

internal object FirebaseRuntimeConfigurator {
    @Volatile
    private var configured = false

    fun configure(context: Context) {
        val shouldConfigure = !configured && FirebaseApp.getApps(context).isNotEmpty()
        if (!shouldConfigure) {
            return
        }

        synchronized(this) {
            if (!configured && FirebaseApp.getApps(context).isNotEmpty()) {
                val auth = FirebaseAuth.getInstance()
                val firestore = FirebaseFirestore.getInstance()

                if (BuildConfig.USE_FIREBASE_EMULATORS) {
                    auth.useEmulator(
                        BuildConfig.FIREBASE_AUTH_EMULATOR_HOST,
                        BuildConfig.FIREBASE_AUTH_EMULATOR_PORT,
                    )
                    firestore.useEmulator(
                        BuildConfig.FIREBASE_FIRESTORE_EMULATOR_HOST,
                        BuildConfig.FIREBASE_FIRESTORE_EMULATOR_PORT,
                    )
                    FirebaseFunctions.getInstance(FIREBASE_FUNCTIONS_REGION).useEmulator(
                        BuildConfig.FIREBASE_FUNCTIONS_EMULATOR_HOST,
                        BuildConfig.FIREBASE_FUNCTIONS_EMULATOR_PORT,
                    )
                }

                firestore.firestoreSettings = FirebaseFirestoreSettings.Builder(firestore.firestoreSettings)
                    .setLocalCacheSettings(
                        PersistentCacheSettings.newBuilder().build(),
                    )
                    .build()
                configured = true
            }
        }
    }
}
