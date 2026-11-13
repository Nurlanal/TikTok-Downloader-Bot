package az.sontarix.market.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.sontarix.market.ui.MainViewModel
import az.sontarix.market.ui.components.EmptyState
import az.sontarix.market.ui.components.ProductCard
import az.sontarix.market.ui.components.SectionTitle
import az.sontarix.market.ui.components.StatCard
import az.sontarix.market.ui.theme.StatusColors
import az.sontarix.market.util.DateUtils

@Composable
fun DashboardScreen(
    vm: MainViewModel,
    onOpenProducts: (String) -> Unit,
    onOpenProduct: (Long) -> Unit,
    onAdd: () -> Unit,
    onScan: () -> Unit
) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val today = DateUtils.today()
    val alerts = remember(products) {
        products.filter { it.daysLeft(today) <= 7 }.sortedBy { it.expiryEpochDay }.take(10)
    }
    val categoryCounts = remember(products) {
        products.groupBy { it.category.ifBlank { "Kateqoriyasız" } }.mapValues { it.value.size }
            .entries.sortedByDescending { it.value }.take(6)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header(stats.total, stats.totalQuantity, onAdd, onScan) }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Ümumi məhsullar", stats.total.toString(), Icons.Filled.Inventory, StatusColors.Indigo,
                        Modifier.weight(1f), subtitle = "Miqdar: ${stats.totalQuantity}") { onOpenProducts("all") }
                    StatCard("Müddəti normal olan", stats.normal.toString(), Icons.Filled.CheckCircle, StatusColors.Green,
                        Modifier.weight(1f)) { onOpenProducts("normal") }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Tarixi yaxınlaşan", stats.approaching.toString(), Icons.Filled.HourglassBottom, StatusColors.Yellow,
                        Modifier.weight(1f), subtitle = "7 gün və az: ${stats.week}") { onOpenProducts("approaching") }
                    StatCard("Bu gün bitən", stats.today.toString(), Icons.Filled.EventBusy, StatusColors.DeepOrange,
                        Modifier.weight(1f)) { onOpenProducts("today") }
                }
                Spacer(Modifier.height(12.dp))
                StatCard("Müddəti keçmiş məhsullar", stats.expired.toString(), Icons.Filled.ErrorOutline, StatusColors.Red,
                    Modifier.fillMaxWidth(), subtitle = if (stats.expired > 0) "Rəfdən çıxarılmalıdır!" else "Hər şey qaydasındadır") { onOpenProducts("expired") }
            }
        }
        if (stats.total > 0) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Spacer(Modifier.height(20.dp))
                    SectionTitle("Statistika")
                    StatusDistributionCard(stats.normal, stats.approaching, stats.today, stats.expired)
                    Spacer(Modifier.height(12.dp))
                    CategoryCard(categoryCounts.map { it.key to it.value }, stats.total)
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(20.dp))
                SectionTitle("Son xəbərdarlıqlar") {
                    if (alerts.isNotEmpty()) TextButton(onClick = { onOpenProducts("urgent") }) { Text("Hamısı") }
                }
            }
        }
        if (alerts.isEmpty()) {
            item {
                if (stats.total == 0) {
                    EmptyState(Icons.Outlined.Inventory2, "Hələ məhsul yoxdur",
                        "İlk məhsulunuzu əlavə edin və ya başqa marketdən gələn faylı idxal edin.") {
                        Button(onClick = onAdd) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Məhsul əlavə et") }
                    }
                } else {
                    EmptyState(Icons.Outlined.TaskAlt, "Təcili xəbərdarlıq yoxdur", "Növbəti 7 gündə bitən məhsul yoxdur.")
                }
            }
        } else {
            items(alerts, key = { it.id }) { p ->
                ProductCard(p, settings.warningDays, Modifier.padding(horizontal = 16.dp, vertical = 5.dp), onClick = { onOpenProduct(p.id) })
            }
        }
    }
}

@Composable
private fun Header(total: Int, qty: Long, onAdd: () -> Unit, onScan: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF4338CA), Color(0xFF312E81))))
            .statusBarsPadding()
            .padding(20.dp)
    ) {
        // dekorativ dairələr
        Box(Modifier.size(140.dp).align(Alignment.TopEnd).padding(start = 40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.07f)))
        Column {
            Text("Son Tarix İzləyici", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            Text("Bu gün: ${DateUtils.format(DateUtils.today())}", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text("$total partiya • $qty ədəd məhsul izlənilir", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onAdd, modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF3730A3))
                ) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Əlavə et") }
                FilledTonalButton(
                    onClick = onScan, modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.18f), contentColor = Color.White)
                ) { Icon(Icons.Filled.QrCodeScanner, null); Spacer(Modifier.width(6.dp)); Text("Skan et") }
            }
        }
    }
}

@Composable
private fun StatusDistributionCard(normal: Int, approaching: Int, today: Int, expired: Int) {
    val total = (normal + approaching + today + expired).coerceAtLeast(1)
    val segments = listOf(
        Triple("Normal", normal, StatusColors.Green),
        Triple("Yaxınlaşan", approaching, StatusColors.Yellow),
        Triple("Bu gün", today, StatusColors.DeepOrange),
        Triple("Keçmiş", expired, StatusColors.Red)
    )
    val progress = remember { Animatable(0f) }
    LaunchedEffect(normal, approaching, today, expired) { progress.snapTo(0f); progress.animateTo(1f, tween(900)) }
    val track = MaterialTheme.colorScheme.surfaceVariant
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(120.dp)) {
                    val stroke = 18.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                    var start = -90f
                    segments.forEach { (_, v, c) ->
                        val sweep = 360f * v / total * progress.value
                        if (sweep > 0f) {
                            drawArc(c, start, (sweep - 2f).coerceAtLeast(0.5f), false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                        }
                        start += 360f * v / total * progress.value
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val pct = normal * 100 / total
                    Text("$pct%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    Text("normal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                segments.forEach { (label, v, c) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(c))
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(90.dp))
                        Text(v.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(data: List<Pair<String, Int>>, total: Int) {
    val palette = listOf(StatusColors.Indigo, StatusColors.Sky, StatusColors.Violet, StatusColors.Teal, StatusColors.Pink, StatusColors.Orange)
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Kateqoriyalar üzrə", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            data.forEachIndexed { i, (name, count) ->
                val anim = remember { Animatable(0f) }
                LaunchedEffect(count, total) { anim.animateTo(count.toFloat() / total.coerceAtLeast(1), tween(800, delayMillis = i * 80)) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(110.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        Box(Modifier.fillMaxWidth(anim.value).height(12.dp).clip(RoundedCornerShape(50))
                            .background(Brush.horizontalGradient(listOf(palette[i % palette.size].copy(alpha = 0.7f), palette[i % palette.size]))))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(count.toString(), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(32.dp))
                }
            }
        }
    }
}
