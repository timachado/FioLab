package com.timachado.fiolab.core.embroidery

import kotlin.math.floor
import kotlin.math.max

data class TextHoopAutoFitResult(
    val heightMm: Float,
    val design: EmbroideryDesign,
    val widthFillRatio: Float,
    val heightFillRatio: Float
)

object TextHoopAutoFit {
    const val MIN_HEIGHT_MM =
        4f

    const val MAX_HEIGHT_MM =
        60f

    private const val TARGET_FILL =
        0.98f

    private const val MAX_SEARCH_PASSES =
        5

    private const val SEARCH_EPSILON_MM =
        0.08f

    fun fit(
        hoop: HoopProfile,
        minHeightMm: Float =
            MIN_HEIGHT_MM,
        maxHeightMm: Float =
            MAX_HEIGHT_MM,
        generator:
            (Float) ->
                Result<EmbroideryDesign>
    ): Result<TextHoopAutoFitResult> =
        runCatching {
            require(
                minHeightMm > 0f &&
                    maxHeightMm >=
                        minHeightMm
            ) {
                "Intervalo de altura inválido."
            }

            val targetWidth =
                hoop.usableWidthMm *
                    TARGET_FILL

            val targetHeight =
                hoop.usableHeightMm *
                    TARGET_FILL

            fun fitsSafe(
                design: EmbroideryDesign
            ): Boolean =
                HoopValidator
                    .validate(
                        design,
                        hoop
                    )
                    .fits

            fun targetFillRatio(
                design: EmbroideryDesign
            ): Float =
                max(
                    design.bounds
                        .widthMm /
                        targetWidth,
                    design.bounds
                        .heightMm /
                        targetHeight
                )

            fun fitsTarget(
                design: EmbroideryDesign
            ): Boolean =
                targetFillRatio(
                    design
                ) <=
                    1f

            fun result(
                height: Float,
                design: EmbroideryDesign
            ): TextHoopAutoFitResult =
                TextHoopAutoFitResult(
                    heightMm =
                        height,
                    design =
                        design,
                    widthFillRatio =
                        design.bounds
                            .widthMm /
                            hoop.usableWidthMm,
                    heightFillRatio =
                        design.bounds
                            .heightMm /
                            hoop.usableHeightMm
                )

            val minimum =
                generator(
                    minHeightMm
                ).getOrThrow()

            require(
                fitsSafe(
                    minimum
                )
            ) {
                "O texto não cabe na área segura do bastidor " +
                    hoop.displayName +
                    " nem no tamanho mínimo."
            }

            if (
                !fitsTarget(
                    minimum
                )
            ) {
                return@runCatching result(
                    minHeightMm,
                    minimum
                )
            }

            var lowHeight =
                minHeightMm

            var lowDesign =
                minimum

            var highHeight =
                maxHeightMm

            var highIsLimit =
                false

            var highFillRatio:
                Float? =
                null

            repeat(
                MAX_SEARCH_PASSES
            ) {
                if (
                    highHeight -
                        lowHeight <=
                        SEARCH_EPSILON_MM
                ) {
                    return@repeat
                }

                val lowFill =
                    targetFillRatio(
                        lowDesign
                    )
                        .coerceAtLeast(
                            0.001f
                        )

                if (
                    lowFill >=
                        0.9985f
                ) {
                    return@repeat
                }

                val estimated =
                    (
                        lowHeight /
                            lowFill
                        ).coerceIn(
                        lowHeight,
                        highHeight
                    )

                val candidateHeight =
                    if (
                        highIsLimit
                    ) {
                        val upperFill =
                            highFillRatio

                        if (
                            upperFill !=
                                null &&
                            upperFill >
                                lowFill +
                                    0.0001f
                        ) {
                            (
                                lowHeight +
                                    (
                                        1f -
                                            lowFill
                                        ) *
                                    (
                                        highHeight -
                                            lowHeight
                                        ) /
                                    (
                                        upperFill -
                                            lowFill
                                        )
                                )
                                .coerceIn(
                                    lowHeight +
                                        0.05f,
                                    highHeight -
                                        0.05f
                                )
                        } else {
                            (
                                lowHeight +
                                    highHeight
                                ) /
                                2f
                        }
                    } else {
                        estimated
                            .coerceAtLeast(
                                lowHeight +
                                    0.1f
                            )
                            .coerceAtMost(
                                highHeight
                            )
                    }

                if (
                    candidateHeight -
                        lowHeight <
                        0.045f
                ) {
                    return@repeat
                }

                val candidate =
                    generator(
                        candidateHeight
                    )
                        .getOrNull()

                if (
                    candidate ==
                        null ||
                    !fitsTarget(
                        candidate
                    )
                ) {
                    highHeight =
                        candidateHeight

                    highFillRatio =
                        candidate?.let {
                            targetFillRatio(
                                it
                            )
                        }

                    highIsLimit =
                        true
                } else {
                    lowHeight =
                        candidateHeight

                    lowDesign =
                        candidate

                    if (
                        candidateHeight >=
                            maxHeightMm -
                                SEARCH_EPSILON_MM
                    ) {
                        return@repeat
                    }
                }
            }

            val roundedDown =
                (
                    floor(
                        lowHeight *
                            10f
                    ) /
                        10f
                    ).coerceIn(
                    minHeightMm,
                    maxHeightMm
                )

            val roundedDesign =
                if (
                    roundedDown <
                        lowHeight -
                            0.001f
                ) {
                    generator(
                        roundedDown
                    )
                        .getOrNull()
                } else {
                    null
                }

            if (
                roundedDesign !=
                    null &&
                fitsSafe(
                    roundedDesign
                )
            ) {
                result(
                    roundedDown,
                    roundedDesign
                )
            } else {
                result(
                    lowHeight,
                    lowDesign
                )
            }
        }

    fun dominantFillRatio(
        result: TextHoopAutoFitResult
    ): Float =
        max(
            result.widthFillRatio,
            result.heightFillRatio
        )
}
