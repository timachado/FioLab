package com.timachado.fiolab.core.embroidery

enum class HoopProfile(
    val displayName: String,
    val widthMm: Float,
    val heightMm: Float,
    val safeMarginMm: Float = 5f
) {
    H100X100(
        "100 × 100 mm",
        100f,
        100f
    ),
    H130X180(
        "130 × 180 mm",
        180f,
        130f
    ),
    H140X200(
        "140 × 200 mm",
        200f,
        140f
    ),
    H160X260(
        "160 × 260 mm",
        260f,
        160f
    ),
    H200X300(
        "200 × 300 mm",
        300f,
        200f
    );

    val usableWidthMm: Float
        get() =
            (
                widthMm -
                    safeMarginMm *
                        2f
                ).coerceAtLeast(
                    1f
                )

    val usableHeightMm: Float
        get() =
            (
                heightMm -
                    safeMarginMm *
                        2f
                ).coerceAtLeast(
                    1f
                )
}

data class HoopFitResult(
    val fits: Boolean,
    val widthOverflowMm: Float,
    val heightOverflowMm: Float,
    val usableWidthMm: Float,
    val usableHeightMm: Float,
    val rotated90: Boolean = false,
    val frameWidthMm: Float = usableWidthMm,
    val frameHeightMm: Float = usableHeightMm
)

object HoopValidator {
    fun validate(
        design: EmbroideryDesign,
        hoop: HoopProfile
    ): HoopFitResult =
        validateOrientation(
            design =
                design,
            hoop =
                hoop,
            rotated90 =
                false
        )

    /**
     * Validação usada no visualizador de matrizes importadas.
     *
     * O PE-DESIGN permite girar a Página de desenho em 90 graus para
     * bastidores retangulares. Por isso, na visualização, escolhemos
     * automaticamente a orientação do mesmo bastidor que melhor acomoda
     * a matriz, sem girar os pontos do arquivo.
     */
    fun validateForViewer(
        design: EmbroideryDesign,
        hoop: HoopProfile
    ): HoopFitResult {
        val normal =
            validateOrientation(
                design =
                    design,
                hoop =
                    hoop,
                rotated90 =
                    false
            )

        if (
            hoop.widthMm ==
                hoop.heightMm
        ) {
            return normal
        }

        val rotated =
            validateOrientation(
                design =
                    design,
                hoop =
                    hoop,
                rotated90 =
                    true
            )

        if (
            normal.fits &&
            !rotated.fits
        ) {
            return normal
        }

        if (
            rotated.fits &&
            !normal.fits
        ) {
            return rotated
        }

        val normalOverflow =
            normal.widthOverflowMm +
                normal.heightOverflowMm

        val rotatedOverflow =
            rotated.widthOverflowMm +
                rotated.heightOverflowMm

        return if (
            rotatedOverflow <
                normalOverflow
        ) {
            rotated
        } else {
            normal
        }
    }

    fun recommendedForViewer(
        design: EmbroideryDesign
    ): HoopProfile =
        HoopProfile
            .entries
            .firstOrNull {
                    hoop ->
                validateForViewer(
                    design =
                        design,
                    hoop =
                        hoop
                ).fits
            }
            ?: HoopProfile
                .entries
                .last()

    private fun validateOrientation(
        design: EmbroideryDesign,
        hoop: HoopProfile,
        rotated90: Boolean
    ): HoopFitResult {
        val frameWidth =
            if (
                rotated90
            ) {
                hoop.heightMm
            } else {
                hoop.widthMm
            }

        val frameHeight =
            if (
                rotated90
            ) {
                hoop.widthMm
            } else {
                hoop.heightMm
            }

        val usableWidth =
            (
                frameWidth -
                    hoop.safeMarginMm *
                        2f
                ).coerceAtLeast(
                1f
            )

        val usableHeight =
            (
                frameHeight -
                    hoop.safeMarginMm *
                        2f
                ).coerceAtLeast(
                1f
            )

        val widthOverflow =
            (
                design.bounds.widthMm -
                    usableWidth
                ).coerceAtLeast(
                    0f
                )

        val heightOverflow =
            (
                design.bounds.heightMm -
                    usableHeight
                ).coerceAtLeast(
                    0f
                )

        return HoopFitResult(
            fits =
                widthOverflow <=
                    0f &&
                    heightOverflow <=
                        0f,
            widthOverflowMm =
                widthOverflow,
            heightOverflowMm =
                heightOverflow,
            usableWidthMm =
                usableWidth,
            usableHeightMm =
                usableHeight,
            rotated90 =
                rotated90,
            frameWidthMm =
                frameWidth,
            frameHeightMm =
                frameHeight
        )
    }
}

enum class FabricProfile(
    val displayName: String,
    val helperText: String,
    val satinDensityMm: Float,
    val pullCompensationMm: Float,
    val underlayMode: SatinUnderlayMode,
    val shortStitches: Boolean
) {
    COTTON(
        displayName =
            "Algodão",
        helperText =
            "Tecido estável • ponto de partida equilibrado",
        satinDensityMm =
            0.45f,
        pullCompensationMm =
            0.20f,
        underlayMode =
            SatinUnderlayMode.CENTER,
        shortStitches =
            true
    ),
    KNIT(
        displayName =
            "Malha / camiseta",
        helperText =
            "Mais elástico • reforço e compensação maiores",
        satinDensityMm =
            0.50f,
        pullCompensationMm =
            0.35f,
        underlayMode =
            SatinUnderlayMode.BOTH,
        shortStitches =
            true
    ),
    TOWEL(
        displayName =
            "Toalha / felpudo",
        helperText =
            "Superfície alta • underlay forte para levantar o bordado",
        satinDensityMm =
            0.50f,
        pullCompensationMm =
            0.40f,
        underlayMode =
            SatinUnderlayMode.BOTH,
        shortStitches =
            true
    ),
    DENIM(
        displayName =
            "Jeans / sarja",
        helperText =
            "Tecido rígido • evita excesso de massa de pontos",
        satinDensityMm =
            0.52f,
        pullCompensationMm =
            0.20f,
        underlayMode =
            SatinUnderlayMode.CENTER,
        shortStitches =
            true
    ),
    CAP(
        displayName =
            "Boné estruturado",
        helperText =
            "Base rígida/curva • underlay firme e cobertura mais fechada",
        satinDensityMm =
            0.42f,
        pullCompensationMm =
            0.30f,
        underlayMode =
            SatinUnderlayMode.BOTH,
        shortStitches =
            true
    )
}
