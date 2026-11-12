package az.catdir.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.catdir.app.AppViewModel
import az.catdir.app.model.Role
import az.catdir.app.ui.theme.Gradients

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(vm: AppViewModel) {
    val user by vm.user.collectAsStateWithLifecycle()
    val u = user ?: return
    var name by rememberSaveable(u.uid) { mutableStateOf(u.name) }
    var phone by rememberSaveable(u.uid) { mutableStateOf(u.phone) }
    var address by rememberSaveable(u.uid) { mutableStateOf(u.address) }

    Scaffold(snackbarHost = { AppSnackbarHost() }, topBar = { TopAppBar(title = { Text("Profil") }) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GradientCard(colors = when (u.role) { Role.CUSTOMER -> Gradients.Hero; Role.COURIER -> Gradients.Teal; Role.ADMIN -> Gradients.Indigo }) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon3D(Icons.Filled.Person, Gradients.Amber, size = 56.dp)
                    HSpace(14.dp)
                    Column {
                        Text(u.name, color = Color.White, style = MaterialTheme.typography.titleLarge)
                        Text("${u.role.label} · ${u.email}", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (u.role == Role.COURIER) {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (u.online) "Xəttdəsiniz" else "Xəttdən kənar", style = MaterialTheme.typography.titleMedium)
                            Text("Xəttdə olanda yeni sifarişləri görürsünüz", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = u.online, onCheckedChange = { vm.setOnline(it) })
                    }
                }
            }
            Field(name, { name = it.take(50) }, "Ad, soyad", Icons.Filled.Person)
            Field(phone, { phone = it.filter { c -> c.isDigit() || c in "+ -()" }.take(20) }, "Telefon", Icons.Filled.Phone, KeyboardOptions(keyboardType = KeyboardType.Phone))
            if (u.role == Role.CUSTOMER) Field(address, { address = it.take(200) }, "Əsas çatdırılma ünvanı", Icons.Filled.Home, singleLine = false)
            Button(onClick = { vm.updateProfile(name, phone, address) }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                Text("Yadda saxla")
            }
            if (vm.backend.isDemo) {
                Text("Demo: rolu dəyiş", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Role.entries.forEach { r ->
                        OutlinedButton(onClick = { vm.demoSignIn(r) }, enabled = r != u.role, modifier = Modifier.weight(1f)) { Text(r.label) }
                    }
                }
            }
            OutlinedButton(onClick = { vm.signOut() }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.AutoMirrored.Filled.Logout, null); HSpace(8.dp); Text("Çıxış")
            }
            Text(
                if (vm.backend.isDemo) "Demo rejim: server qoşulmayıb" else "Server: Firebase",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
