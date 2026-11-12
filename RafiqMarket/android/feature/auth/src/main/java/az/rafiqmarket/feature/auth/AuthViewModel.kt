package az.rafiqmarket.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.rafiqmarket.core.data.AuthRepository
import az.rafiqmarket.core.network.apiCall
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val step: Step = Step.PHONE,
    val phoneInput: String = "",
    val normalizedPhone: String = "",
    val isNewUser: Boolean = false,
    val code: String = "",
    val name: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val resendIn: Int = 0,
) { enum class Step { PHONE, CODE } }

@HiltViewModel
class AuthViewModel @Inject constructor(private val repo: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state
    private var timer: Job? = null

    fun onPhone(v: String) = _state.update { it.copy(phoneInput = v.filter { c -> c.isDigit() }.take(9), error = null) }
    fun onCode(v: String) = _state.update { it.copy(code = v.filter { c -> c.isDigit() }.take(6), error = null) }
    fun onName(v: String) = _state.update { it.copy(name = v.take(60), error = null) }
    fun back() = _state.update { it.copy(step = AuthUiState.Step.PHONE, code = "", error = null) }

    fun requestCode() {
        val s = _state.value
        if (s.phoneInput.length != 9) { _state.update { it.copy(error = "Nömrəni tam daxil edin: 50 123 45 67") }; return }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            apiCall { repo.requestOtp("+994${s.phoneInput}") }
                .onSuccess { r ->
                    _state.update { it.copy(loading = false, step = AuthUiState.Step.CODE, normalizedPhone = r.phone, isNewUser = r.isNewUser, code = "") }
                    startTimer()
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    private fun startTimer() {
        timer?.cancel()
        timer = viewModelScope.launch {
            for (i in 60 downTo 0) { _state.update { it.copy(resendIn = i) }; delay(1000) }
        }
    }

    fun verify(onSuccess: () -> Unit) {
        val s = _state.value
        if (s.code.length != 6) { _state.update { it.copy(error = "6 rəqəmli kodu daxil edin") }; return }
        if (s.isNewUser && s.name.trim().length < 2) { _state.update { it.copy(error = "Adınızı daxil edin") }; return }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            apiCall { repo.verifyOtp(s.normalizedPhone, s.code, s.name.trim()) }
                .onSuccess { _state.update { it.copy(loading = false) }; onSuccess() }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }
}
