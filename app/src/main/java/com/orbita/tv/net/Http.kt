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
    /**
     * callTimeoutSeconds es el unico limite que corta de verdad una peticion
     * colgada. Los otros solo vigilan huecos entre bytes, asi que una respuesta
     * que llega a goteo puede tardar minutos sin dispararlos. Para el video vale
     * 0, porque un canal es una lectura sin fin; para las consultas del panel
     * TIENE que ser finito, o un withTimeoutOrNull de fuera no sirve de nada:
     * la llamada es bloqueante y no se deja interrumpir.
     */
    fun client(
        net: NetSettings,
        readTimeoutSeconds: Long = 30,
        callTimeoutSeconds: Long = 0,
    ): OkHttpClient {
        val b = OkHttpClient.Builder()
            .dns(SmartDns(net.forceIpv4, net.useDoh))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(callTimeoutSeconds, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
        if (net.forceHttp11) b.protocols(listOf(Protocol.HTTP_1_1))
        return b.build()
    }
}
