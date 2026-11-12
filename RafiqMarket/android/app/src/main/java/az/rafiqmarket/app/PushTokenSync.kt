package az.rafiqmarket.app

import android.content.Context
import android.util.Log
import az.rafiqmarket.core.network.RafiqApi
import az.rafiqmarket.core.network.dto.FcmTokenBody
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FCM tokenini serverə göndərir. Firebase konfiqurasiya olunmayıbsa (google-services.json yoxdur)
 * push bildirişləri deaktivdir – tətbiq WebSocket bildirişləri ilə işləməyə davam edir.
 */
@Singleton
class PushTokenSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: RafiqApi,
) {
    val available: Boolean
        get() = BuildConfig.FIREBASE_CONFIGURED && FirebaseApp.getApps(context).isNotEmpty()

    suspend fun sync() {
        if (!available) { Log.i("Push", "Firebase konfiqurasiya edilməyib – push deaktiv"); return }
        runCatching {
            val token = FirebaseMessaging.getInstance().token.await()
            api.putFcmToken(FcmTokenBody(token))
        }.onFailure { Log.w("Push", "token göndərilmədi: ${it.message}") }
    }

    suspend fun send(token: String) {
        runCatching { api.putFcmToken(FcmTokenBody(token)) }
    }
}
