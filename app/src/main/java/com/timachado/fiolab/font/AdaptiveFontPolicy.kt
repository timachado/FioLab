package com.timachado.fiolab.font

import kotlin.math.hypot

enum class ImportedGlyphTechnique {
    AXIS_SATIN,
    AREA_FILL
}

data class ImportedGlyphMetrics(
    val areaPixels: Int,
    val skeletonPixels: Int,
    val branchPixels: Int,
    val widthPixels: Int,
    val heightPixels: Int
) {
    val estimatedStrokeWidthPixels: Float
        get() =
            if (
                skeletonPixels <=
                    0
            ) {
                Float.POSITIVE_INFINITY
            } else {
                areaPixels.toFloat() /
                    skeletonPixels
            }

    val branchRatio: Float
        get() =
            if (
                skeletonPixels <=
                    0
            ) {
                1f
            } else {
                branchPixels.toFloat() /
                    skeletonPixels
            }
}

data class GeneratedStitchQuality(
    val stitchCount: Int,
    val jumpCount: Int,
    val maxStitchLengthUnits: Double,
    val zeroLengthStitches: Int
) {
    val zeroLengthRatio: Float
        get() =
            if (
                stitchCount <=
                    0
            ) {
                1f
            } else {
                zeroLengthStitches.toFloat() /
                    stitchCount
            }
}

/**
 * Política genérica para fontes importadas.
 *
 * Não usa nome/família de fonte. A decisão é baseada exclusivamente na
 * geometria rasterizada de cada glifo, permitindo que o mesmo motor trabalhe
 * com fontes cursivas, serifadas, sans, display e pesos variados.
 */
object AdaptiveFontPolicy {

    fun chooseTechnique(
        metrics: ImportedGlyphMetrics
    ): ImportedGlyphTechnique {
        if (
            metrics.areaPixels <=
                0 ||
            metrics.skeletonPixels <=
                0
        ) {
            return ImportedGlyphTechnique
                .AREA_FILL
        }

        val stroke =
            metrics
                .estimatedStrokeWidthPixels

        val branchRatio =
            metrics
                .branchRatio

        val verySmall =
            metrics.widthPixels <
                5 ||
                metrics.heightPixels <
                    5

        val veryWideStroke =
            stroke >
                24f

        val excessiveBranching =
            branchRatio >
                0.18f

        val skeletonTooShort =
            metrics.skeletonPixels <
                5

        return if (
            verySmall ||
            veryWideStroke ||
            excessiveBranching ||
            skeletonTooShort
        ) {
            ImportedGlyphTechnique
                .AREA_FILL
        } else {
            ImportedGlyphTechnique
                .AXIS_SATIN
        }
    }

    fun isAcceptable(
        quality:
            GeneratedStitchQuality
    ): Boolean =
        quality.stitchCount >
            0 &&
            quality.maxStitchLengthUnits <=
                125.0 &&
            quality.zeroLengthRatio <=
                0.08f &&
            quality.jumpCount <=
                quality.stitchCount +
                    32

    fun qualityFromCoordinates(
        commands:
            List<Triple<Int, Int, Boolean>>
    ): GeneratedStitchQuality {
        var stitchCount =
            0

        var jumpCount =
            0

        var maxLength =
            0.0

        var zeroLength =
            0

        var previousX:
            Int? =
            null

        var previousY:
            Int? =
            null

        commands.forEach {
                item ->
            val x =
                item.first

            val y =
                item.second

            val isStitch =
                item.third

            if (
                isStitch
            ) {
                stitchCount++

                if (
                    previousX !=
                        null &&
                    previousY !=
                        null
                ) {
                    val length =
                        hypot(
                            (
                                x -
                                    previousX!!
                                ).toDouble(),
                            (
                                y -
                                    previousY!!
                                ).toDouble()
                        )

                    maxLength =
                        maxOf(
                            maxLength,
                            length
                        )

                    if (
                        length <
                            0.5
                    ) {
                        zeroLength++
                    }
                }
            } else {
                jumpCount++
            }

            previousX =
                x

            previousY =
                y
        }

        return GeneratedStitchQuality(
            stitchCount =
                stitchCount,
            jumpCount =
                jumpCount,
            maxStitchLengthUnits =
                maxLength,
            zeroLengthStitches =
                zeroLength
        )
    }
}
