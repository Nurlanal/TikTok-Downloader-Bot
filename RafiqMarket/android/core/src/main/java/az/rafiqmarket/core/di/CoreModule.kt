package az.rafiqmarket.core.di

import android.content.Context
import androidx.room.Room
import az.rafiqmarket.core.BuildConfig
import az.rafiqmarket.core.db.CartDao
import az.rafiqmarket.core.db.RafiqDatabase
import az.rafiqmarket.core.network.AuthInterceptor
import az.rafiqmarket.core.network.RafiqApi
import az.rafiqmarket.core.network.TokenAuthenticator
import az.rafiqmarket.core.session.SessionStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    @Provides @Singleton @Named("base")
    fun baseClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    @Provides @Singleton
    fun apiClient(@Named("base") base: OkHttpClient, store: SessionStore, json: Json): OkHttpClient {
        val b = base.newBuilder()
            .addInterceptor(AuthInterceptor(store))
            .authenticator(TokenAuthenticator(store, json, base))
        if (BuildConfig.DEBUG) {
            b.addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        }
        return b.build()
    }

    @Provides @Singleton @Named("ws")
    fun wsClient(@Named("base") base: OkHttpClient): OkHttpClient =
        base.newBuilder().pingInterval(25, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS).build()

    @Provides @Singleton
    fun retrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL.ifBlank { "https://invalid.local/" })
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides @Singleton
    fun api(retrofit: Retrofit): RafiqApi = retrofit.create(RafiqApi::class.java)

    @Provides @Singleton
    fun db(@ApplicationContext ctx: Context): RafiqDatabase =
        Room.databaseBuilder(ctx, RafiqDatabase::class.java, "rafiq.db").fallbackToDestructiveMigration().build()

    @Provides fun cartDao(db: RafiqDatabase): CartDao = db.cartDao()
}
