package az.rafiqmarket.feature.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.rafiqmarket.core.ui.components.RafiqLogo
import az.rafiqmarket.core.ui.theme.RafiqColors

/** Vahid giriş: telefon nömrəsi + SMS təsdiqi. Rol serverdən gəlir. */
@Composable
fun AuthScreen(onLoggedIn: () -> Unit, vm: AuthViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(RafiqColors.Green800, RafiqColors.Green600, MaterialTheme.colorScheme.background), endY = 1400f)
        )
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).systemBarsPadding().imePadding().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))
            RafiqLogo(96.dp)
            Spacer(Modifier.height(16.dp))
            Text("Rafiq Market", style = MaterialTheme.typography.headlineMedium, color = androidx.compose.ui.graphics.Color.White)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LocationOn, null, tint = RafiqColors.Lime, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Xıdırlı kəndi daxilində sürətli çatdırılma", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f))
            }
            Spacer(Modifier.height(36.dp))
            ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
                AnimatedContent(s.step, label = "step", transitionSpec = {
                    (slideInHorizontally { it / 3 } + fadeIn()) togetherWith fadeOut()
                }) { step ->
                    Column(Modifier.padding(22.dp)) {
                        when (step) {
                            AuthUiState.Step.PHONE -> PhoneStep(s, vm)
                            AuthUiState.Step.CODE -> CodeStep(s, vm, onLoggedIn)
                        }
                        if (s.error != null) {
                            Spacer(Modifier.height(10.dp))
                            Text(s.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Daxil olmaqla istifadə şərtləri və məxfilik siyasəti ilə razılaşırsınız. Kuryer və admin hesabları market tərəfindən verilir.",
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PhoneStep(s: AuthUiState, vm: AuthViewModel) {
    Text("Daxil ol", style = MaterialTheme.typography.titleLarge)
    Text("Telefon nömrənizə SMS kod göndəriləcək", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(18.dp))
    OutlinedTextField(
        value = s.phoneInput, onValueChange = vm::onPhone, singleLine = true,
        label = { Text("Mobil nömrə") }, placeholder = { Text("50 123 45 67") },
        prefix = { Text("+994 ", fontWeight = FontWeight.SemiBold) },
        leadingIcon = { Icon(Icons.Outlined.Phone, null) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
    )
    Spacer(Modifier.height(18.dp))
    Button(onClick = vm::requestCode, enabled = !s.loading, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
        if (s.loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        else Text("SMS kod al")
    }
}

@Composable
private fun ColumnScope.CodeStep(s: AuthUiState, vm: AuthViewModel, onLoggedIn: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
        Text("Təsdiq kodu", style = MaterialTheme.typography.titleLarge)
    }
    Text("${s.normalizedPhone} nömrəsinə göndərilən 6 rəqəmli kodu daxil edin", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(16.dp))
    if (s.isNewUser) {
        OutlinedTextField(
            value = s.name, onValueChange = vm::onName, singleLine = true, label = { Text("Adınız") },
            leadingIcon = { Icon(Icons.Outlined.Person, null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        )
        Spacer(Modifier.height(10.dp))
    }
    OutlinedTextField(
        value = s.code, onValueChange = vm::onCode, singleLine = true, label = { Text("SMS kod") },
        leadingIcon = { Icon(Icons.Outlined.Lock, null) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        textStyle = MaterialTheme.typography.titleLarge.copy(letterSpacing = MaterialTheme.typography.titleLarge.fontSize * 0.4f),
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
    )
    Spacer(Modifier.height(16.dp))
    Button(onClick = { vm.verify(onLoggedIn) }, enabled = !s.loading, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
        if (s.loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        else Text("Təsdiqlə və daxil ol")
    }
    TextButton(onClick = vm::requestCode, enabled = s.resendIn == 0 && !s.loading, modifier = Modifier.align(Alignment.CenterHorizontally)) {
        Text(if (s.resendIn > 0) "Kodu yenidən göndər (${s.resendIn} san)" else "Kodu yenidən göndər")
    }
}
