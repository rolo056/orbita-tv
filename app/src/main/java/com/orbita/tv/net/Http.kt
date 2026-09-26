package com.orbita.tv.net

import com.orbita.tv.data.NetSettings
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.util.concurrent.TimeUnit

/**
 * Un solo constructor de cliente para la API y para el video, de modo que lo que
 * mide el diagnostico sea exactamente lo que despues usa el reproductor.
 *
 * HTTP/1.1 forzado a proposito: con perdida de paquetes (lo que pasa cada vez
 * que Starlink salta de satelite) HTTP/2 multiplexa todo sobre una sola
 * conexion y un paquete perdido congela el stream entero.
 */
object Http {
    fun client(net: NetSettings, readTimeoutSeconds: Long = 30): OkHttpClient {
        val b = OkHttpClient.Builder()
            .dns(SmartDns(net.forceIpv4, net.useDoh))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS) // el video es una lectura sin fin
            .retryOnConnectionFailure(true)
            .followRedirects(true)
        if (net.forceHttp11) b.protocols(listOf(Protocol.HTTP_1_1))
        return b.build()
    }
}
