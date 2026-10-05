package dev.resonance

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Original 9×9 appearance pictograms by codingEzio, released under GPL-3.0-or-later.
 * Source SHA-256: d8812e4e9edc973a7da1fb516a1eee2283c9f93e63e0a2e4000095f928d92e34 Uses the same
 * 22-unit canvas, 2.25-unit grid and .95-unit dot radius. No runtime dependency.
 */
internal object AppearanceIcons {
    val System =
        dots(
            "System",
            listOf(
                "001111100",
                "010000010",
                "100011001",
                "100011001",
                "100011001",
                "100011001",
                "100011001",
                "010000010",
                "001111100",
            ),
        )
    val Light =
        dots(
            "Light",
            listOf(
                "000010000",
                "010000010",
                "000111000",
                "001000100",
                "101000101",
                "001000100",
                "000111000",
                "010000010",
                "000010000",
            ),
        )
    val Dark =
        dots(
            "Dark",
            listOf(
                "000111000",
                "001100000",
                "011000000",
                "010000001",
                "110000001",
                "110000011",
                "011000110",
                "001111100",
                "000110000",
            ),
        )

    private fun dots(name: String, rows: List<String>): ImageVector {
        val path = buildString {
            rows.forEachIndexed { y, row ->
                row.forEachIndexed { x, pixel ->
                    if (pixel == '1') {
                        append(
                            "M${2 + x * 2.25 - .95} ${2 + y * 2.25}a.95.95 0 1 0 1.9 0a.95.95 0 1 0-1.9 0"
                        )
                    }
                }
            }
        }
        return ImageVector.Builder(name, 22.dp, 22.dp, 22f, 22f)
            .apply {
                addPath(
                    PathParser().parsePathString(path).toNodes(),
                    fill = SolidColor(Color.Black),
                )
            }
            .build()
    }
}
