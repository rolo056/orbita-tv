package com.orbita.tv.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.store by preferencesDataStore(name = "orbita")

/**
 * Persistencia local, sin backend. Las capas de red y de reproducción leen
 * [snapshot] de forma sincrónica; la UI escribe con [save].
 */
object Prefs {
    private val kHost = stringPreferencesKey("host")
    private val kPort = intPreferencesKey("port")
    private val kHttps = booleanPreferencesKey("https")
    private val kUser = stringPreferencesKey("user")
    private val kPass = stringPreferencesKey("pass")
    private val kIpv4 = booleanPreferencesKey("ipv4")
    private val kDoh = booleanPreferencesKey("doh")
    private val kH11 = booleanPreferencesKey("http11")
    private val kOrder = stringPreferencesKey("order")
    private val kBuffer = stringPreferencesKey("buffer")
    private val kUa = stringPreferencesKey("ua")
    private val kStall = intPreferencesKey("stall")
    private val kBuffering = intPreferencesKey("buffering")
    private val kIpLookup = booleanPreferencesKey("iplookup")
    private val kLastChannel = intPreferencesKey("lastChannel")
    private val kSkinUrl = stringPreferencesKey("skinUrl")
    private val kLiveDesign = booleanPreferencesKey("liveDesign")
    private val kFavoritos = stringPreferencesKey("favoritos")
    private val kBiblioteca = stringPreferencesKey("biblioteca")

    @Volatile
    var snapshot: AppSettings = AppSettings()
        private set

    suspend fun load(ctx: Context): AppSettings {
        val p = ctx.store.data.first()
        snapshot = AppSettings(
            account = Account(
                host = p[kHost] ?: "",
                port = p[kPort] ?: 80,
                https = p[kHttps] ?: false,
                username = p[kUser] ?: "",
                password = p[kPass] ?: "",
            ),
            net = NetSettings(
                forceIpv4 = p[kIpv4] ?: true,
                useDoh = p[kDoh] ?: false,
                forceHttp11 = p[kH11] ?: true,
                variantOrder = runCatching { VariantOrder.valueOf(p[kOrder] ?: "") }
                    .getOrDefault(VariantOrder.HLS_FIRST),
                buffer = runCatching { BufferPreset.valueOf(p[kBuffer] ?: "") }
                    .getOrDefault(BufferPreset.SATELITE),
                userAgent = p[kUa] ?: NetSettings.DEFAULT_UA,
                stallSeconds = p[kStall] ?: 8,
                bufferingSeconds = p[kBuffering] ?: 15,
                allowIpLookup = p[kIpLookup] ?: false,
            ),
            lastChannelId = p[kLastChannel] ?: -1,
            skinUrl = p[kSkinUrl] ?: AppSettings.DEFAULT_SKIN_URL,
            liveDesign = p[kLiveDesign] ?: false,
            favorites = (p[kFavoritos] ?: "").split(",")
                .mapNotNull { it.trim().toIntOrNull() }.toSet(),
        )
        return snapshot
    }

    suspend fun save(ctx: Context, s: AppSettings) {
        snapshot = s
        ctx.store.edit { p ->
            p[kHost] = s.account.host
            p[kPort] = s.account.port
            p[kHttps] = s.account.https
            p[kUser] = s.account.username
            p[kPass] = s.account.password
            p[kIpv4] = s.net.forceIpv4
            p[kDoh] = s.net.useDoh
            p[kH11] = s.net.forceHttp11
            p[kOrder] = s.net.variantOrder.name
            p[kBuffer] = s.net.buffer.name
            p[kUa] = s.net.userAgent
            p[kStall] = s.net.stallSeconds
            p[kBuffering] = s.net.bufferingSeconds
            p[kIpLookup] = s.net.allowIpLookup
            p[kLastChannel] = s.lastChannelId
            p[kSkinUrl] = s.skinUrl
            p[kLiveDesign] = s.liveDesign
            p[kFavoritos] = s.favorites.joinToString(",")
        }
    }

    suspend fun clear(ctx: Context) = save(ctx, AppSettings())

    /** Peliculas y series a medias, y las favoritas. Va aparte de los ajustes. */
    suspend fun loadLibrary(ctx: Context): Biblioteca =
        Biblioteca.deJson(ctx.store.data.first()[kBiblioteca])

    suspend fun saveLibrary(ctx: Context, b: Biblioteca) {
        ctx.store.edit { p -> p[kBiblioteca] = b.aJson() }
    }
}
