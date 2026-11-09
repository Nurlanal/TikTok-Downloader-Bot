package az.payreport.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import az.payreport.app.AppContainer
import az.payreport.app.PayReportApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val app = LocalContext.current.applicationContext as PayReportApp
    return viewModel(key = key, factory = viewModelFactory { initializer { create(app.container) } })
}

/** Dövrləri hər dəqiqə yeniləyir (gecə yarısı keçəndə "bugün" dəyişsin). */
fun <T> ticker(intervalMs: Long = 60_000, produce: () -> T): Flow<T> = flow {
    while (true) {
        emit(produce())
        delay(intervalMs)
    }
}.distinctUntilChanged()

/** Admin panelinin PIN kilidi (proses ərzində). */
object AdminSession {
    @Volatile var unlocked: Boolean = false
}
