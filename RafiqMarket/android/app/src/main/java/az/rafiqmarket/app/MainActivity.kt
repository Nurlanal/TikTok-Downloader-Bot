package az.rafiqmarket.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.rafiqmarket.core.network.dto.Role
import az.rafiqmarket.core.ui.components.LoadingBox
import az.rafiqmarket.core.ui.theme.RafiqTheme
import az.rafiqmarket.feature.admin.AdminRoot
import az.rafiqmarket.feature.auth.AuthScreen
import az.rafiqmarket.feature.courier.CourierRoot
import az.rafiqmarket.feature.customer.CustomerRoot
import dagger.hilt.android.AndroidEntryPoint

/**
 * Tək giriş nöqtəsi. Müştəri, kuryer və admin EYNİ APK-dan istifadə edir;
 * server rolu təsdiqlədikdən sonra uyğun interfeys açılır.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val vm: RootViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { vm.state.value == RootState.Loading }
        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            val theme by vm.theme.collectAsStateWithLifecycle()
            val state by vm.state.collectAsStateWithLifecycle()
            val pendingOrder by vm.pendingOrderId.collectAsStateWithLifecycle()
            RafiqTheme(theme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    LifecycleResumeEffect(Unit) { vm.verifyWithServer(); onPauseOrDispose { } }
                    NotificationPermission(state is RootState.LoggedIn)
                    AnimatedContent(state, label = "root", transitionSpec = { fadeIn() togetherWith fadeOut() }, contentKey = {
                        when (it) { is RootState.LoggedIn -> "${it.userId}-${it.role}"; else -> it.toString() }
                    }) { s ->
                        when (s) {
                            RootState.Loading -> LoadingBox()
                            RootState.LoggedOut -> AuthScreen(onLoggedIn = {})
                            is RootState.LoggedIn -> when (s.role) {
                                Role.CUSTOMER -> CustomerRoot(pendingOrder)
                                Role.COURIER -> CourierRoot(pendingOrder)
                                Role.ADMIN -> AdminRoot(pendingOrder)
                            }
                        }
                    }
                    LaunchedEffect(pendingOrder) { if (pendingOrder != null) vm.pendingOrderId.value = null }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getLongExtra(EXTRA_ORDER_ID, -1L)?.takeIf { it > 0 }?.let { vm.pendingOrderId.value = it }
        // rafiqmarket://payment – ödəniş səhifəsindən qayıdış: sifariş ekranı onResume-da serverdən statusu yoxlayır
    }

    companion object { const val EXTRA_ORDER_ID = "orderId" }
}

@Composable
private fun NotificationPermission(loggedIn: Boolean) {
    if (Build.VERSION.SDK_INT < 33 || !loggedIn) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}
