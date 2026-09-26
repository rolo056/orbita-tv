package com.orbita.tv.net

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Resolucion de nombres a prueba de Starlink.
 *
 * Dos fallas reales que esto corrige:
 *  1. El panel publica un registro AAAA que en realidad no escucha. El TV
 *     intenta IPv6 primero y se queda colgado hasta el tiempo de espera.
 *     forceIpv4 descarta esos registros y va directo a IPv4.
 *  2. El DNS del router Starlink devuelve respuestas lentas o vacias para los
 *     dominios dinamicos que usan los paneles. useDoh resuelve contra
 *     Cloudflare por HTTPS y saltea el router.
 *
 * Cuando no se fuerza IPv4, igual se ordena IPv4 primero: es lo que responden
 * casi todos los paneles.
 */
class SmartDns(
    private val forceIpv4: Boolean,
    private val useDoh: Boolean,
) : Dns {

    private val doh: DnsOverHttps? by lazy {
        if (!useDoh) {
            null
        } else {
            runCatching {
                DnsOverHttps.Builder()
                    .client(OkHttpClient.Builder().build())
                    .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
                    .bootstrapDnsHosts(
                        InetAddress.getByName("1.1.1.1"),
                        InetAddress.getByName("1.0.0.1"),
                    )
                    .includeIPv6(!forceIpv4)
                    .build()
            }.getOrNull()
        }
    }

    override fun lookup(hostname: String): List<InetAddress> {
        // Una IP literal no necesita resolverse.
        if (hostname.firstOrNull()?.isDigit() == true) {
            runCatching { return listOf(InetAddress.getByName(hostname)) }
        }

        val found = LinkedHashSet<InetAddress>()
        doh?.let { resolver -> runCatching { found += resolver.lookup(hostname) } }
        if (found.isEmpty()) runCatching { found += Dns.SYSTEM.lookup(hostname) }

        var list: List<InetAddress> = found.toList()
        list = if (forceIpv4) {
            val v4 = list.filterIsInstance<Inet4Address>()
            if (v4.isEmpty()) list else v4
        } else {
            list.sortedBy { if (it is Inet4Address) 0 else 1 }
        }
        if (list.isEmpty()) throw UnknownHostException(hostname)
        return list
    }
}
