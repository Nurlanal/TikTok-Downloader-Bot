package az.siqaret.anbar.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.siqaret.anbar.InventoryViewModel
import az.siqaret.anbar.data.HistoryEntry
import az.siqaret.anbar.data.HistoryType
import az.siqaret.anbar.ui.components.EmptyState
import az.siqaret.anbar.ui.components.HSpace
import az.siqaret.anbar.ui.components.AppSnackbarHost
import az.siqaret.anbar.ui.components.Icon3D
import az.siqaret.anbar.ui.components.PeriodChips
import az.siqaret.anbar.ui.components.ThemeMenuButton
import az.siqaret.anbar.ui.theme.Gradients
import az.siqaret.anbar.ui.theme.NegativeColor
import az.siqaret.anbar.ui.theme.PositiveColor
import az.siqaret.anbar.util.DaySummary
import az.siqaret.anbar.util.Fmt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(vm: InventoryViewModel) {
    val all by vm.history.collectAsStateWithLifecycle()
    val period by vm.historyPeriod.collectAsStateWithLifecycle()
    val theme by vm.themeMode.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    val from = period.from()
    val days = remember(all, period) { DaySummary.group(all.filter { it.timestamp >= from }) }

    Scaffold(
        contentWindowInsets = TabScreenInsets,
        snackbarHost = { AppSnackbarHost() },
        topBar = {
            TopAppBar(
                title = { Text("Tarixçə") },
                actions = {
                    if (all.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) { Icon(Icons.Filled.DeleteSweep, "Tarixçəni təmizlə") }
                    }
                    ThemeMenuButton(theme, vm::setTheme)
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = pad.calculateTopPadding() + 4.dp, bottom = pad.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { PeriodChips(period, { vm.historyPeriod.value = it }) }
            if (days.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Filled.History, "Tarixçə boşdur",
                        "Məhsul əlavə etdikdə, stoku dəyişdikdə və ya redaktə etdikdə burada qeyd görünəcək.",
                    )
                }
            }
            days.forEach { day ->
                stickyHeader(key = "h${day.dayStart}") { DayHeader(day) }
                items(day.entries, key = { it.id }) { HistoryRow(it) }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { Icon3D(Icons.Filled.DeleteSweep, Gradients.Red, size = 52.dp) },
            title = { Text("Tarixçə təmizlənsin?") },
            text = { Text("Bütün dəyişiklik qeydləri silinəcək. Məhsullar və stok dəyişməyəcək.") },
            confirmButton = {
                Button(
                    onClick = { vm.clearHistory(); confirmClear = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Təmizlə") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Ləğv et") } },
        )
    }
}

@Composable
private fun DayHeader(day: DaySummary) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(Fmt.dayTitle(day.dayStart), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "${day.entries.size} əməliyyat",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("+${Fmt.count(day.blocksIn)}", color = PositiveColor, fontWeight = FontWeight.Bold)
        HSpace(10.dp)
        Text("−${Fmt.count(day.blocksOut)}", color = NegativeColor, fontWeight = FontWeight.Bold)
        HSpace(4.dp)
        Text("blok", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun HistoryRow(h: HistoryEntry) {
    val (icon, colors) = when (h.type) {
        HistoryType.ADDED -> Icons.Filled.AddBox to Gradients.Violet
        HistoryType.EDITED -> Icons.Filled.Edit to Gradients.Blue
        HistoryType.DELETED -> Icons.Filled.Delete to Gradients.Red
        HistoryType.RESTORED -> Icons.Filled.Restore to Gradients.Indigo
        HistoryType.STOCK_IN -> Icons.Filled.Add to Gradients.Green
        HistoryType.STOCK_OUT -> Icons.Filled.Remove to Gradients.Rose
    }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon3D(icon, colors, size = 40.dp)
            HSpace(12.dp)
            Column(Modifier.weight(1f)) {
                Text(h.productName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    h.type.label + if (h.note.isNotBlank()) " · ${h.note}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            HSpace(8.dp)
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${Fmt.signed(h.blockDelta)} blok",
                    fontWeight = FontWeight.Bold,
                    color = when {
                        h.blockDelta > 0 -> PositiveColor
                        h.blockDelta < 0 -> NegativeColor
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(Fmt.time(h.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
