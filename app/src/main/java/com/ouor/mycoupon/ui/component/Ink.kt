package com.ouor.mycoupon.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * design-system §R.5: the app's one icon set — 24 grid, 1.75 stroke, round caps, no fills (the redesign mockup's `svg.i`).
 * Tinted like any icon (`Icon(Ink.Search, …)`), so the colour still comes from the caller or LocalContentColor.
 */
object Ink {
    private fun c(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"
    private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 -$r ${r}h-${w - 2 * r}a$r $r 0 0 1 -$r -${r}v-${h - 2 * r}a$r $r 0 0 1 $r -${r}z"

    private fun icon(name: String, vararg d: String, mirror: Boolean = false): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f, autoMirror = mirror).apply {
            d.forEach { addPath(addPathNodes(it), stroke = SolidColor(Color.Black), strokeLineWidth = 1.75f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) }
        }.build()

    private val ring = c(12f, 12f, 8.5f)
    private const val BELL = "M18 9a6 6 0 1 0-12 0c0 6.5-2 8-2 8h16s-2-1.5-2-8M10.3 21a1.9 1.9 0 0 0 3.4 0"
    private val calendar = rect(4f, 5f, 16f, 15f, 2f)

    val Search by lazy { icon("search", c(11f, 11f, 6.5f), "m20 20-4.2-4.2") }
    val SearchOff by lazy { icon("searchOff", c(11f, 11f, 6.5f), "m20 20-4.2-4.2", "M8.5 8.5l5 5") }
    val Settings by lazy {
        icon("settings", c(12f, 12f, 3f), "M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z")
    }
    val Back by lazy { icon("back", "M19 12H5M11 18l-6-6 6-6", mirror = true) }
    val Close by lazy { icon("close", "M6 6l12 12M18 6 6 18") }
    val More by lazy { icon("more", c(12f, 5.5f, .6f), c(12f, 12f, .6f), c(12f, 18.5f, .6f)) }
    val Plus by lazy { icon("plus", "M12 5v14M5 12h14") }
    val Sort by lazy { icon("sort", "M4 7h16M7 12h10M10 17h4") }
    val ChevronLeft by lazy { icon("chevronLeft", "m15 6-6 6 6 6", mirror = true) }
    val ChevronRight by lazy { icon("chevronRight", "m9 6 6 6-6 6", mirror = true) }
    val ExpandMore by lazy { icon("expandMore", "m6 9 6 6 6-6") }
    val ExpandLess by lazy { icon("expandLess", "m6 15 6-6 6 6") }
    val Bell by lazy { icon("bell", BELL) }
    val BellRing by lazy { icon("bellRing", BELL, "M4 3.5 2.5 6M20 3.5 21.5 6") }
    val BellOff by lazy { icon("bellOff", "M8.7 3.9A6 6 0 0 1 18 9c0 2.4.5 4.2 1.1 5.4M17 17H4s2-1.5 2-8c0-.6.1-1.2.3-1.8M10.3 21a1.9 1.9 0 0 0 3.4 0M3 3l18 18") }
    val Clock by lazy { icon("clock", ring, "M12 7.5V12l3 2") }
    val Alert by lazy { icon("alert", ring, "M12 7.5v5M12 16.2v.3") }
    val Info by lazy { icon("info", ring, "M12 11v5M12 7.8v.3") }
    val Warning by lazy { icon("warning", "M12 4 2.5 20h19z", "M12 10v4M12 17v.3") }
    val CheckCircle by lazy { icon("checkCircle", ring, "m8.5 12 2.5 2.5 4.5-5") }
    val Block by lazy { icon("block", ring, "M6 6l12 12") }
    val Sun by lazy { icon("sun", c(12f, 12f, 4f), "M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4") }
    val Copy by lazy { icon("copy", rect(8f, 8f, 12f, 12f, 2f), "M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2") }
    val Image by lazy { icon("image", rect(3.5f, 4.5f, 17f, 15f, 2f), c(9f, 10f, 1.6f), "m20.5 16-5-5-9 8.5") }
    val ImageOff by lazy { icon("imageOff", rect(3.5f, 4.5f, 17f, 15f, 2f), "m20.5 16-5-5-9 8.5", "M3 3l18 18") }
    val ImageSearch by lazy { icon("imageSearch", "M13 19.5H5.5a2 2 0 0 1-2-2v-11a2 2 0 0 1 2-2h11a2 2 0 0 1 2 2V10", c(8.5f, 9.5f, 1.5f), "m3.5 16 4-4 3 3", c(17f, 16f, 3f), "m21.5 20.5-2.4-2.4") }
    val Photos by lazy { icon("photos", rect(7f, 3f, 14f, 14f, 2f), "M3 7v12a2 2 0 0 0 2 2h12", "m21 13-4-4-7 7") }
    val Camera by lazy { icon("camera", "M4 8h3l2-3h6l2 3h3a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1z", c(12f, 13f, 3.5f)) }
    val Send by lazy { icon("send", "M21 3 10 14M21 3l-7 18-4-7-7-4z", mirror = true) }
    val Share by lazy { icon("share", c(18f, 5f, 2.5f), c(6f, 12f, 2.5f), c(18f, 19f, 2.5f), "M8.2 13.3l7.6 4.4M15.8 6.3l-7.6 4.4") }
    val Store by lazy { icon("store", "M4 10v10h16V10M3 4h18l-1 6H4zM9 20v-5h6v5") }
    val Ticket by lazy { icon("ticket", "M3 7a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v3a2 2 0 0 0 0 4v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-3a2 2 0 0 0 0-4z", "M14 5v2M14 11v2M14 17v2") }
    val Gift by lazy { icon("gift", rect(3f, 8f, 18f, 4f, 1f), "M12 8v13M19 12v9H5v-9", "M12 8S10.5 3 8 3a2.5 2.5 0 0 0 0 5M12 8s1.5-5 4-5a2.5 2.5 0 0 1 0 5") }
    val Calendar by lazy { icon("calendar", calendar, "M4 10h16M8 3v4M16 3v4") }
    val EventBusy by lazy { icon("eventBusy", calendar, "M4 10h16M8 3v4M16 3v4", "m10 13 4 4M14 13l-4 4") }
    val Lock by lazy { icon("lock", rect(5f, 11f, 14f, 10f, 2f), "M8 11V7a4 4 0 0 1 8 0v4") }
    val PhoneLock by lazy { icon("phoneLock", rect(6f, 2.5f, 12f, 19f, 2.5f), "M10.5 18.5h3", rect(9.5f, 10.5f, 5f, 4f, 1f), "M10.5 10.5V9a1.5 1.5 0 0 1 3 0v1.5") }
    val Trash by lazy { icon("trash", "M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3") }
    val Edit by lazy { icon("edit", "M4 20h4L19 9l-4-4L4 16z", "M13.5 6.5l4 4") }
    val Eye by lazy { icon("eye", "M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z", c(12f, 12f, 3f)) }
    val EyeOff by lazy { icon("eyeOff", "M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z", c(12f, 12f, 3f), "M3 3l18 18") }
    val Volume by lazy { icon("volume", "M11 5 6 9H3v6h3l5 4z", "M15.5 8.5a5 5 0 0 1 0 7M18.5 5.5a9 9 0 0 1 0 13", mirror = true) }
    val Bug by lazy { icon("bug", rect(7f, 7f, 10f, 13f, 5f), "M9 7a3 3 0 0 1 6 0M12 11v9M3 13h4M17 13h4M4 7l3 2M20 7l-3 2M4 20l3-2M20 20l-3-2") }
    val Touch by lazy { icon("touch", "M9 11V5.5a1.5 1.5 0 0 1 3 0V11M12 10.5a1.5 1.5 0 0 1 3 0V12M15 11.5a1.5 1.5 0 0 1 3 0V15a6 6 0 0 1-6 6h-.5a6 6 0 0 1-4.6-2.2L4.5 15.5a1.5 1.5 0 0 1 2.3-1.9L9 16") }
}
