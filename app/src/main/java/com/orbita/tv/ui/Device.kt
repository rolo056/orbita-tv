package com.orbita.tv.ui

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Si estamos en un televisor o en un aparato con pantalla tactil.
 *
 * No se usa el tamano de la pantalla para decidir esto: hay tabletas mas grandes
 * que televisores chicos, y lo que cambia no es el tamano sino como se maneja.
 * En un televisor hay mando y no hay dedo, asi que los controles en pantalla
 * solo estorban; en un telefono no hay mando, y sin controles en pantalla no hay
 * forma de cambiar de canal.
 *
 * Se pregunta de varias maneras porque ninguna es infalible: algunas cajas
 * Android no declaran leanback pero si se reportan como televisor al sistema, y
 * otras no declaran nada de eso pero tampoco tienen pantalla tactil, que al
 * final es lo unico que importa aca.
 */
@Composable
fun isTvDevice(): Boolean {
    val ctx = LocalContext.current
    return remember(ctx) { detectTv(ctx) }
}

private fun detectTv(ctx: Context): Boolean {
    val pm = ctx.packageManager
    if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return true
    if (pm.hasSystemFeature("android.hardware.type.television")) return true
    val modo = ctx.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    if (modo == Configuration.UI_MODE_TYPE_TELEVISION) return true
    val uiMode = ctx.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    if (uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
    // Sin pantalla tactil no hay dedo: se maneja con mando, sea lo que sea.
    return !pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
}
