package az.payreport.app.ui.payment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.payreport.app.core.format.Fmt
import az.payreport.app.core.parser.PhoneUtils
import az.payreport.app.data.db.AuditEntity
import az.payreport.app.data.db.PaymentEntity
import az.payreport.app.data.db.PaymentStatus
import az.payreport.app.data.repo.PaymentDraft
import az.payreport.app.data.repo.PaymentRepository
import az.payreport.app.data.db.AppDatabase
import az.payreport.app.ui.common.appViewModel
import az.payreport.app.ui.components.KeyValue
import az.payreport.app.ui.components.StatusChip
import az.payreport.app.ui.components.ThinDivider
import az.payreport.app.ui.components.sourceName
import az.payreport.app.ui.theme.AppTheme
import az.payreport.app.ui.theme.MoneyStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PaymentActionsViewModel(private val repo: PaymentRepository, db: AppDatabase, paymentId: Long) : ViewModel() {
    val payment: StateFlow<PaymentEntity?> = db.payments().observeById(paymentId).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val audit: StateFlow<List<AuditEntity>> = db.audit().observeForPayment(paymentId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val message = MutableStateFlow<String?>(null)

    fun run(block: suspend PaymentRepository.() -> Unit, ok: String) = viewModelScope.launch {
        message.value = try {
            repo.block(); ok
        } catch (e: Exception) {
            e.message ?: "Xəta baş verdi"
        }
    }
}

private fun auditActionAz(a: String) = when (a) {
    "CREATE" -> "Yaradıldı"
    "UPDATE" -> "Redaktə edildi"
    "CONFIRM" -> "Təsdiqləndi"
    "CANCEL" -> "Ləğv edildi"
    else -> a
}

/** Ödəniş detalları: məlumat, xam mətn, audit tarixçəsi və admin əməliyyatları. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentDetailSheet(paymentId: Long, allowActions: Boolean, onDismiss: () -> Unit) {
    val vm = appViewModel(key = "payment-$paymentId") { PaymentActionsViewModel(it.repository, it.db, paymentId) }
    val p by vm.payment.collectAsState()
    val audit by vm.audit.collectAsState()
    val msg by vm.message.collectAsState()
    var editing by remember { mutableStateOf(false) }
    var cancelling by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        val pay = p
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()) {
            if (pay == null) {
                Text("Yüklənir…")
                return@Column
            }
            Row {
                Column(Modifier.weight(1f)) {
                    Text(PhoneUtils.format(pay.phone), style = MaterialTheme.typography.titleLarge)
                    Text("#${pay.id} · ${sourceName(pay.source)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusChip(pay.status)
            }
            Spacer(Modifier.height(10.dp))
            Text(Fmt.azn(pay.amountMinor), style = MoneyStyle.copy(fontSize = 30.sp),
                color = if (pay.status == PaymentStatus.CONFIRMED) AppTheme.extra.positive else MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            KeyValue("Ödəniş tarixi", Fmt.dateTime(pay.paidAt))
            KeyValue("Əməliyyat ID", pay.txnId ?: "—")
            KeyValue("Tanıma dəqiqliyi", "${pay.confidence}%")
            pay.emailFrom?.let { KeyValue("Göndərən", it) }
            pay.emailSubject?.let { KeyValue("Mövzu", it) }
            pay.note?.let { KeyValue("Qeyd", it) }
            pay.cancelReason?.let { KeyValue("Ləğv səbəbi", it, AppTheme.extra.danger) }
            KeyValue("Əlavə edən", pay.createdBy)

            if (msg != null) {
                Spacer(Modifier.height(8.dp))
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                    Text(msg!!, Modifier.padding(10.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (allowActions) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (pay.status == PaymentStatus.PENDING) {
                        Button(onClick = { vm.run({ confirm(pay.id) }, "Ödəniş təsdiqləndi") }, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = AppTheme.extra.positive)) {
                            Icon(Icons.Outlined.CheckCircle, null); Spacer(Modifier.padding(3.dp)); Text("Təsdiqlə")
                        }
                    }
                    if (pay.status != PaymentStatus.CANCELLED) {
                        OutlinedButton(onClick = { editing = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.Edit, null); Spacer(Modifier.padding(3.dp)); Text("Redaktə")
                        }
                        OutlinedButton(onClick = { cancelling = true }, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppTheme.extra.danger)) {
                            Icon(Icons.Outlined.Block, null); Spacer(Modifier.padding(3.dp)); Text("Ləğv")
                        }
                    }
                }
            }

            if (!pay.rawText.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                Text("Bildirişin mətni", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                    Text(pay.rawText.take(3000), Modifier.padding(10.dp), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Audit tarixçəsi", style = MaterialTheme.typography.titleSmall)
            audit.forEach { a ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text("${auditActionAz(a.action)} · ${a.actor}", style = MaterialTheme.typography.bodyMedium)
                    Text(Fmt.dateTime(a.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    a.details?.let { Text(it.take(400), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace) }
                }
                ThinDivider()
            }
            if (audit.isEmpty()) Text("Qeyd yoxdur", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
        }

        if (editing && pay != null) {
            PaymentEditDialog(
                initial = pay,
                onDismiss = { editing = false },
                onSave = { d -> editing = false; vm.run({ update(pay.id, d) }, "Dəyişikliklər saxlanıldı") },
            )
        }
        if (cancelling && pay != null) {
            CancelDialog(onDismiss = { cancelling = false }) { reason ->
                cancelling = false
                vm.run({ cancel(pay.id, reason) }, "Ödəniş ləğv edildi (audit tarixçəsində saxlanılır)")
            }
        }
    }
}

@Composable
fun CancelDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ödənişi ləğv et") },
        text = {
            Column {
                Text("Ləğv edilən ödəniş mədaxilə daxil edilmir, lakin silinmir və audit tarixçəsində qalır.")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(reason, { reason = it }, label = { Text("Səbəb") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(reason) }) { Text("Ləğv et", color = AppTheme.extra.danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Bağla") } },
    )
}

/** Ödəniş əlavə etmə / redaktə dialoqu. */
@Composable
fun PaymentEditDialog(initial: PaymentEntity?, onDismiss: () -> Unit, onSave: (PaymentDraft) -> Unit) {
    var phone by remember { mutableStateOf(initial?.phone?.let { PhoneUtils.format(it) } ?: "") }
    var amount by remember { mutableStateOf(initial?.amountMinor?.let { Fmt.amount(it).replace(" ", "") } ?: "") }
    var date by remember { mutableStateOf(Fmt.userDateTime(initial?.paidAt ?: System.currentTimeMillis())) }
    var txn by remember { mutableStateOf(initial?.txnId ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Yeni ödəniş" else "Ödənişi redaktə et") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(phone, { phone = it }, label = { Text("Telefon nömrəsi") }, placeholder = { Text("055 777 66 55") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(amount, { amount = it }, label = { Text("Məbləğ (AZN)") }, placeholder = { Text("5,00") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(date, { date = it }, label = { Text("Tarix (gg.aa.iiii ss:dd)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(txn, { txn = it }, label = { Text("Əməliyyat ID (istəyə görə)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("Qeyd") }, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val minor = Fmt.parseAmountInput(amount)
                val ts = Fmt.parseUserDateTime(date)
                error = when {
                    PhoneUtils.normalize(phone) == null -> "Telefon nömrəsi düzgün deyil"
                    minor == null -> "Məbləğ düzgün deyil"
                    ts == null -> "Tarix formatı: 09.11.2026 14:30"
                    else -> null
                }
                if (error == null) onSave(PaymentDraft(phone, minor!!, ts!!, txn.ifBlank { null }, note.ifBlank { null }))
            }) { Text("Saxla") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Ləğv") } },
    )
}

@Composable
fun rememberScope() = rememberCoroutineScope()
