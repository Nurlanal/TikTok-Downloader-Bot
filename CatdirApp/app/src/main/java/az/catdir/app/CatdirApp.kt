package az.catdir.app

import android.app.Application
import az.catdir.app.data.Backend
import az.catdir.app.data.DemoBackend
import az.catdir.app.data.FirebaseBackend
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CatdirApp : Application() {
    lateinit var backend: Backend
        private set

    override fun onCreate() {
        super.onCreate()
        backend = createBackend()
    }

    /** google-services.json ilə yığılıbsa Firebase, yoxsa demo rejim. */
    private fun createBackend(): Backend = runCatching {
        val app = FirebaseApp.getApps(this).firstOrNull() ?: FirebaseApp.initializeApp(this)
        if (app == null) DemoBackend()
        else FirebaseBackend(FirebaseAuth.getInstance(app), FirebaseFirestore.getInstance(app))
    }.getOrElse { DemoBackend() }
}
