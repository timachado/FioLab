package com.timachado.fiolab.core.embroidery

enum class StitchCommand { STITCH, JUMP, COLOR_CHANGE, SEQUIN, END }

data class EmbroideryPoint(
    val xUnits: Int,
    val yUnits: Int,
    val command: StitchCommand,
    val colorIndex: Int
)

data class EmbroideryBounds(
    val minXUnits: Int,
    val maxXUnits: Int,
    val minYUnits: Int,
    val maxYUnits: Int
) {
    val widthMm: Float get() = (maxXUnits - minXUnits) / 10f
    val heightMm: Float get() = (maxYUnits - minYUnits) / 10f
}

data class EmbroideryDesign(
    val fileName: String,
    val format: String,
    val label: String?,
    val points: List<EmbroideryPoint>,
    val bounds: EmbroideryBounds,
    val stitchCount: Int,
    val jumpCount: Int,
    val colorChanges: Int,
    val endFound: Boolean,
    val sourceBytes: ByteArray
) {
    val colorCount: Int get() = if (points.isEmpty()) 0 else colorChanges + 1
}

sealed interface EmbroideryLoadResult {
    data class Success(val design: EmbroideryDesign) : EmbroideryLoadResult
    data class Error(val userMessage: String, val technicalMessage: String? = null) : EmbroideryLoadResult
}
