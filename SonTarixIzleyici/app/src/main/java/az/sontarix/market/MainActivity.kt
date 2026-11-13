package az.sontarix.market

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.sontarix.market.notify.NotificationHelper
import az.sontarix.market.ui.AppRoot
import az.sontarix.market.ui.MainViewModel
import az.sontarix.market.ui.theme.SonTarixTheme
import kotlinx.coroutines.flow.MutableStateFlow

/** Xaricdən gələn naviqasiya hədəfləri (bildiriş, fayl açma). */
sealed interface DeepLink {
    data class OpenProduct(val id: Long) : DeepLink
    data class OpenScreen(val route: String) : DeepLink
    data class ImportFile(val uri: Uri) : DeepLink
}

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val deepLink = MutableStateFlow<DeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            SonTarixTheme(settings.themeMode) {
                AppRoot(vm = vm, deepLink = deepLink)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val productId = intent.getLongExtra(NotificationHelper.EXTRA_PRODUCT_ID, -1L)
        when {
            productId > 0 -> deepLink.value = DeepLink.OpenProduct(productId)
            intent.getStringExtra(NotificationHelper.EXTRA_OPEN) != null ->
                deepLink.value = DeepLink.OpenScreen(intent.getStringExtra(NotificationHelper.EXTRA_OPEN)!!)
            intent.action == Intent.ACTION_VIEW && intent.data != null -> deepLink.value = DeepLink.ImportFile(intent.data!!)
            intent.action == Intent.ACTION_SEND -> {
                val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
                if (uri != null) deepLink.value = DeepLink.ImportFile(uri)
            }
        }
    }
}
