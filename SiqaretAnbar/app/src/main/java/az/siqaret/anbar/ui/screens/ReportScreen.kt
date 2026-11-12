package az.siqaret.anbar.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.siqaret.anbar.InventoryViewModel
import az.siqaret.anbar.data.totalAmount
import az.siqaret.anbar.ui.components.EmptyState
import az.siqaret.anbar.ui.components.GradientCard
import az.siqaret.anbar.ui.components.AppSnackbarHost
import az.siqaret.anbar.ui.components.Icon3D
import az.siqaret.anbar.ui.components.PeriodChips
import az.siqaret.anbar.ui.components.SectionTitle
import az.siqaret.anbar.ui.components.ThemeMenuButton
import az.siqaret.anbar.ui.components.VSpace
import az.siqaret.anbar.ui.theme.Gradients
import az.siqaret.anbar.ui.theme.NegativeColor
import az.siqaret.anbar.ui.theme.PositiveColor
import az.siqaret.anbar.util.DaySummary
import az.siqaret.anbar.util.Fmt
import az.siqaret.anbar.util.PdfExporter
import az.siqaret.anbar.util.XlsxExporter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(vm: InventoryViewModel) {
    val all by vm.products.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val period by vm.reportPeriod.collectAsStateWithLifecycle()
    val theme by vm.themeMode.collectAsStateWithLifecycle()

    val from = period.from()
    val days = remember(history, period) { DaySummary.group(history.filter { it.timestamp >= from }) }
    val rows = all

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PdfExporter.MIME)) { uri ->
        if (uri != null) vm.export(uri, pdf = true)
    }
    val xlsxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XlsxExporter.MIME)) { uri ->
        if (uri != null) vm.export(uri, pdf = false)
    }

    Scaffold(
        contentWindowInsets = TabScreenInsets,
        snackbarHost = { AppSnackbarHost() },
        topBar = {
            TopAppBar(title = { Text("Hesabat") }, actions = { ThemeMenuButton(theme, vm::setTheme) })
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = pad.calculateTopPadding() + 4.dp, bottom = pad.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                GradientCard {
                    Column(Modifier.padding(20.dp)) {
                        Text("Ümumi stok dəyəri", color = Color.White.copy(alpha = 0.85f))
                        Text(
                            Fmt.money(stats.totalValue), color = Color.White,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                        )
                        VSpace(12.dp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MiniStat("Məhsul", Fmt.count(stats.productCount))
                            MiniStat("Blok", Fmt.count(stats.totalBlocks))
                            MiniStat("Qutu", Fmt.count(stats.totalBoxes))
                        }
                    }
                }
            }
            item { SectionTitle("İxrac et") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ExportCard(
                        Icons.Filled.PictureAsPdf, Gradients.Red, "PDF", "Çap üçün hazır hesabat",
                        Modifier.weight(1f),
                    ) { pdfLauncher.launch(vm.suggestedFileName(pdf = true)) }
                    ExportCard(
                        Icons.Filled.TableChart, Gradients.Green, "Excel", "Formullu .xlsx cədvəl",
                        Modifier.weight(1f),
                    ) { xlsxLauncher.launch(vm.suggestedFileName(pdf = false)) }
                }
            }
            item {
                Text(
                    "Tarixçə dövrü (hesabata daxil ediləcək):",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                VSpace(6.dp)
                PeriodChips(period, { vm.reportPeriod.value = it })
            }

            item { SectionTitle("Məhsulların siyahısı") }
            if (rows.isEmpty()) {
                item { EmptyState(Icons.Filled.Assessment, "Məhsul yoxdur", "Hesabat üçün əvvəlcə məhsul əlavə edin") }
            } else {
                item { TableHeader() }
                itemsIndexed(rows, key = { _, p -> p.id }) { i, p ->
                    TableRow(
                        i % 2 == 1,
                        p.name, Fmt.count(p.blockCount), Fmt.number(p.blockPrice), Fmt.number(p.totalAmount),
                    )
                }
                item {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
                            Cell("CƏMİ", 2.2f, bold = true)
                            Cell(Fmt.count(stats.totalBlocks), 1f, bold = true, end = true)
                            Cell("", 1.3f)
                            Cell(Fmt.money(stats.totalValue), 1.7f, bold = true, end = true)
                        }
                    }
                }
            }

            item { SectionTitle("Gündəlik dəyişikliklər · ${period.label}") }
            if (days.isEmpty()) {
                item {
                    Text(
                        "Bu dövrdə dəyişiklik yoxdur",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
            days.forEach { d ->
                item(key = "d${d.dayStart}") {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(Fmt.dayTitle(d.dayStart), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${d.entries.size} əməliyyat",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("+${Fmt.count(d.blocksIn)} blok", color = PositiveColor, fontWeight = FontWeight.Bold)
                                Text("−${Fmt.count(d.blocksOut)} blok", color = NegativeColor, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.MiniStat(label: String, value: String) {
    Surface(
        color = Color.White.copy(alpha = 0.16f), shape = RoundedCornerShape(14.dp),
        modifier = Modifier.weight(1f),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(label, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
            Text(value, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
    }
}

@Composable
private fun ExportCard(
    icon: ImageVector, colors: List<Color>, title: String, subtitle: String,
    modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon3D(icon, colors, size = 56.dp)
            VSpace(10.dp)
            Text("$title ixrac et", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                subtitle, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TableHeader() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        val c = MaterialTheme.colorScheme.onPrimary
        Cell("Ad", 2.2f, bold = true, color = c)
        Cell("Blok", 1f, bold = true, end = true, color = c)
        Cell("Blok qiy.", 1.3f, bold = true, end = true, color = c)
        Cell("Məbləğ", 1.7f, bold = true, end = true, color = c)
    }
}

@Composable
private fun TableRow(zebra: Boolean, vararg values: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (zebra) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Cell(values[0], 2.2f)
        Cell(values[1], 1f, end = true)
        Cell(values[2], 1.3f, end = true)
        Cell(values[3], 1.7f, end = true, bold = true)
    }
}

@Composable
private fun RowScope.Cell(
    text: String, weight: Float, bold: Boolean = false, end: Boolean = false,
    color: Color = Color.Unspecified,
) {
    Text(
        text,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = if (end) TextAlign.End else TextAlign.Start,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        color = color,
    )
}
