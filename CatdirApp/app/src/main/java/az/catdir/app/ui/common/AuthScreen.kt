package az.catdir.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import az.catdir.app.AppViewModel
import az.catdir.app.model.Role
import az.catdir.app.ui.theme.Gradients

@Composable
fun AuthScreen(vm: AppViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPw by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    Scaffold(snackbarHost = { AppSnackbarHost() }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GradientCard {
                Row(Modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Çatdır", color = Color.White, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold))
                        Text("Mağazalardan qapınıza qədər", color = Color.White.copy(alpha = 0.9f))
                    }
                    Icon3D(Icons.Filled.DeliveryDining, Gradients.Amber, size = 64.dp)
                }
            }

            if (vm.backend.isDemo) {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Demo rejim", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text(
                            "Server (Firebase) hələ qoşulmayıb. Məlumatlar yalnız bu telefonda saxlanılır və proqram bağlananda silinir. " +
                                "Bütün axını yoxlamaq üçün rolu seçin:",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        DemoRoleButton("Müştəri kimi daxil ol", Icons.Filled.ShoppingBag, Gradients.Orange) { vm.demoSignIn(Role.CUSTOMER) }
                        DemoRoleButton("Kuryer kimi daxil ol", Icons.Filled.DeliveryDining, Gradients.Teal) { vm.demoSignIn(Role.COURIER) }
                        DemoRoleButton("Admin kimi daxil ol", Icons.Filled.AdminPanelSettings, Gradients.Indigo) { vm.demoSignIn(Role.ADMIN) }
                    }
                }
            }

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(tab == 0, { tab = 0 }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Daxil ol") }
                SegmentedButton(tab == 1, { tab = 1 }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Qeydiyyat") }
            }

            if (tab == 1) {
                Field(name, { name = it.take(50) }, "Ad, soyad", Icons.Filled.Person, KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))
                Field(phone, { phone = it.filter { c -> c.isDigit() || c in "+ -()" }.take(20) }, "Telefon", Icons.Filled.Phone, KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next))
            }
            Field(email, { email = it.trim().take(80) }, "E-poçt", Icons.Filled.Email, KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
            OutlinedTextField(
                value = password, onValueChange = { password = it.take(64) },
                label = { Text("Parol") }, singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Lock, null) },
                trailingIcon = {
                    IconButton(onClick = { showPw = !showPw }) {
                        Icon(if (showPw) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Parolu göstər")
                    }
                },
                visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    busy = true
                    if (tab == 0) vm.signIn(email, password) { busy = false }
                    else vm.register(name, phone, email, password) { busy = false }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
            ) { Text(if (tab == 0) "Daxil ol" else "Hesab yarat") }
            if (tab == 1) {
                Text(
                    "Yeni hesab müştəri kimi yaradılır. Kuryer və admin rollarını admin təyin edir.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DemoRoleButton(text: String, icon: ImageVector, colors: List<Color>, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon3D(icon, colors, size = 36.dp)
            HSpace(12.dp)
            Text(text, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, label: String, icon: ImageVector?, keyboard: KeyboardOptions = KeyboardOptions.Default, modifier: Modifier = Modifier.fillMaxWidth(), singleLine: Boolean = true) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = singleLine,
        leadingIcon = icon?.let { { Icon(it, null) } },
        keyboardOptions = keyboard, shape = RoundedCornerShape(16.dp), modifier = modifier,
    )
}

