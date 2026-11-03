package com.gympad.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

object GymIcons {
    val Menu = strokeIcon("menu", "M4 7h16", "M4 12h16", "M4 17h16")
    val Calendar = strokeIcon(
        "calendar",
        "M8 2v4",
        "M16 2v4",
        "M3 10h18",
        "M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
    )
    val Check = strokeIcon("check", "M20 6 9 17l-5-5", width = 2.6f)
    val ChevronDown = strokeIcon("chevron-down", "m6 9 6 6 6-6")
    val ChevronUp = strokeIcon("chevron-up", "m18 15-6-6-6 6")
    val Plus = strokeIcon("plus", "M5 12h14", "M12 5v14")
    val Minus = strokeIcon("minus", "M5 12h14")
    val Close = strokeIcon("close", "M18 6 6 18", "M6 6l12 12")
    val Back = strokeIcon("back", "M19 12H5", "M12 19l-7-7 7-7")
    val ArrowUp = strokeIcon("arrow-up", "m5 12 7-7 7 7", "M12 19V5")
    val ArrowDown = strokeIcon("arrow-down", "M12 5v14", "m19 12-7 7-7-7")
    val Swap = strokeIcon("swap", "M8 3 4 7l4 4", "M4 7h16", "m16 21 4-4-4-4", "M20 17H4")
    val Pencil = strokeIcon("pencil", "M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z")
    val More = strokeIcon("more", "M12 5h.01", "M12 12h.01", "M12 19h.01", width = 3f)
    val Lock = strokeIcon(
        "lock",
        "M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2z",
        "M7 11V7a5 5 0 0 1 10 0v4",
    )
    val Routines = strokeIcon("routines", "M8 6h13", "M8 12h13", "M8 18h13", "M3 6h.01", "M3 12h.01", "M3 18h.01")
    val Export = strokeIcon("export", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "M17 8l-5-5-5 5", "M12 3v12")
    val Import = strokeIcon("import", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "M7 10l5 5 5-5", "M12 15V3")
}

private fun strokeIcon(name: String, vararg paths: String, width: Float = 2f): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach { data ->
            addPath(
                pathData = PathParser().parsePathString(data).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
