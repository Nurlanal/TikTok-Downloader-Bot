package az.rafiqmarket.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import az.rafiqmarket.core.BuildConfig

/** Google Maps açarı yoxdursa xəritə əvəzinə aydın xəbərdarlıq göstərilir */
object MapsSupport { val keyConfigured: Boolean get() = BuildConfig.MAPS_KEY_SET }

@Composable
fun MapsKeyMissing(modifier: Modifier = Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Outlined.Map, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text("Xəritə aktiv deyil: Google Maps API açarı təyin edilməyib (MAPS_API_KEY).",
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
