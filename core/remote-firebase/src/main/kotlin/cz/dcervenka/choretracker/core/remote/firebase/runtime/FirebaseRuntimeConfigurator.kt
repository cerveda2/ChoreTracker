package cz.dcervenka.choretracker.core.remote.firebase.runtime

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import cz.dcervenka.choretracker.core.remote.firebase.BuildConfig

internal object FirebaseRuntimeConfigurator {
    @Volatile
    private var configured = false

    fun configure(context: Context) {
        if (configured) return
        synchronized(this) {
            if (configured) return
            if (FirebaseApp.getApps(context).isEmpty()) return

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
