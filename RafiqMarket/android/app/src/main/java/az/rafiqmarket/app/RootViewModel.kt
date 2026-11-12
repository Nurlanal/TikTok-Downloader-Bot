package az.rafiqmarket.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.rafiqmarket.core.data.AuthRepository
import az.rafiqmarket.core.network.AppException
import az.rafiqmarket.core.network.apiCall
import az.rafiqmarket.core.network.dto.Role
import az.rafiqmarket.core.realtime.RealtimeClient
import az.rafiqmarket.core.session.SessionStore
import az.rafiqmarket.core.session.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RootState {
    data object Loading : RootState
    data object LoggedOut : RootState
    data class LoggedIn(val role: Role, val userId: Long) : RootState
}

/**
 * Giriş etmiş istifadəçinin rolunu SERVERDƏN alır və uyğun interfeysi seçir.
 * Rol dəyişdikdə (məs. admin müştərini kuryer etdi) interfeys avtomatik dəyişir.
 */
@HiltViewModel
class RootViewModel @Inject constructor(
    private val store: SessionStore,
    private val auth: AuthRepository,
    private val realtime: RealtimeClient,
    private val push: PushTokenSync,
) : ViewModel() {
    val theme = store.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)
    val state: StateFlow<RootState> = store.session
        .map { s -> if (s == null) RootState.LoggedOut else RootState.LoggedIn(s.user.roleEnum, s.user.id) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, RootState.Loading)

    val pendingOrderId = MutableStateFlow<Long?>(null)

    init {
        viewModelScope.launch {
            state.collect { s ->
                when (s) {
                    is RootState.LoggedIn -> { realtime.connect(); push.sync() }
                    RootState.LoggedOut -> realtime.disconnect()
                    RootState.Loading -> Unit
                }
            }
        }
    }

    /** Hər açılışda / ön plana qayıtdıqda rolu və hesab statusunu serverdən yoxla */
    fun verifyWithServer() = viewModelScope.launch {
        if (store.current() == null) return@launch
        apiCall { auth.refreshMe() }.onFailure { e ->
            // Hesab deaktiv edilibsə – çıxış
            if ((e as? AppException)?.httpStatus == 403) auth.logout()
        }
        realtime.connect()
    }
}
