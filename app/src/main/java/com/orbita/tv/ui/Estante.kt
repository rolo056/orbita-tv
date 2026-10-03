package com.orbita.tv.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.orbita.tv.net.Category
import com.orbita.tv.net.Xtream
import kotlinx.coroutines.CancellationException

/**
 * El catalogo de una seccion (peliculas o series): sus categorias, la que esta
 * abierta y los titulos de esa categoria.
 *
 * Lo ya pedido se guarda en memoria, unas pocas categorias: volver a una que
 * se acaba de ver no deberia costar otro viaje al servidor por el satelite.
 */
class Estante<T> {
    var categorias by mutableStateOf<List<Category>>(emptyList())
    var abierta by mutableStateOf<String?>(null)
    var titulos by mutableStateOf<List<T>>(emptyList())
        private set
    var cargando by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private val enMemoria = LinkedHashMap<String, List<T>>()

    // Cada pedido lleva un numero. Si mientras llegaba se eligio otra
    // categoria, la respuesta vieja se descarta en vez de pisar a la nueva.
    private var pedido = 0

    fun vaciar() {
        pedido++
        categorias = emptyList()
        abierta = null
        titulos = emptyList()
        cargando = false
        error = null
        enMemoria.clear()
    }

    /** Una categoria del panel. Lo ya pedido sale de memoria. */
    suspend fun abrir(id: String, traer: suspend (String) -> List<T>) {
        abierta = id
        error = null
        val guardada = enMemoria[id]
        if (guardada != null) {
            pedido++
            titulos = guardada
            cargando = false
            return
        }
        val mio = ++pedido
        titulos = emptyList()
        cargando = true
        try {
            val lista = traer(id)
            if (mio != pedido) return
            enMemoria[id] = lista
            while (enMemoria.size > EN_MEMORIA) enMemoria.remove(enMemoria.keys.first())
            titulos = lista
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (mio == pedido) error = Xtream.mensajeAmigable(e)
        } finally {
            if (mio == pedido) cargando = false
        }
    }

    /** Una categoria que no viene del panel: seguir viendo, favoritas. */
    fun abrirLocal(id: String) {
        pedido++
        abierta = id
        error = null
        cargando = false
    }

    private companion object {
        const val EN_MEMORIA = 8
    }
}
