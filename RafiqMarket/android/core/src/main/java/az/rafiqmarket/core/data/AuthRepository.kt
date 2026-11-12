package az.rafiqmarket.core.data

import az.rafiqmarket.core.network.RafiqApi
import az.rafiqmarket.core.network.dto.*
import az.rafiqmarket.core.realtime.RealtimeClient
import az.rafiqmarket.core.session.SessionStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: RafiqApi,
    private val store: SessionStore,
    private val cart: CartRepository,
    private val realtime: RealtimeClient,
) {
    suspend fun requestOtp(phone: String) = api.requestOtp(OtpRequestBody(phone))

    suspend fun verifyOtp(phone: String, code: String, name: String?): UserDto {
        val auth = api.verifyOtp(OtpVerifyBody(phone, code, name?.takeIf { it.isNotBlank() }))
        store.save(auth)
        return auth.user
    }

    /** Rol HƏR açılışda serverdən yoxlanılır */
    suspend fun refreshMe(): UserDto {
        val me = api.me()
        store.updateUser(me)
        return me
    }

    suspend fun updateName(name: String): UserDto = api.updateName(NameBody(name)).also { store.updateUser(it) }

    suspend fun logout() {
        val s = store.current()
        runCatching { api.logout(LogoutBody(s?.refreshToken)) }
        realtime.disconnect()
        cart.clear()
        store.clear()
    }
}
