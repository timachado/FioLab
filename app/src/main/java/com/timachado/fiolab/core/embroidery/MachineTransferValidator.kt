package com.timachado.fiolab.core.embroidery

enum class TransferIssueLevel {
    BLOCKING,
    WARNING
}

data class TransferIssue(
    val level: TransferIssueLevel,
    val message: String
)

data class MachineTransferValidation(
    val format: String,
    val hoop: HoopProfile,
    val issues: List<TransferIssue>
) {
    val blockingIssues: List<TransferIssue>
        get() =
            issues.filter {
                it.level ==
                    TransferIssueLevel.BLOCKING
            }

    val warnings: List<TransferIssue>
        get() =
            issues.filter {
                it.level ==
                    TransferIssueLevel.WARNING
            }

    val ready: Boolean
        get() =
            blockingIssues.isEmpty()
}

object MachineTransferValidator {
    fun recommendedHoop(
        design: EmbroideryDesign
    ): HoopProfile =
        design.hoopProfile
            ?.takeIf {
                HoopValidator
                    .validate(
                        design,
                        it
                    )
                    .fits
            }
            ?: HoopProfile
                .entries
                .firstOrNull {
                    HoopValidator
                        .validate(
                            design,
                            it
                        )
                        .fits
                }
            ?: HoopProfile
                .entries
                .last()

    fun validate(
        design: EmbroideryDesign,
        format: String,
        hoop: HoopProfile
    ): MachineTransferValidation {
        val normalizedFormat =
            format
                .trim()
                .uppercase()

        val issues =
            buildList {
                if (
                    normalizedFormat !in
                        MatrixConverter
                            .supportedFormats
                ) {
                    add(
                        TransferIssue(
                            level =
                                TransferIssueLevel
                                    .BLOCKING,
                            message =
                                "Formato $normalizedFormat não é suportado para envio."
                        )
                    )
                }

                if (
                    design.stitchCount <=
                        0
                ) {
                    add(
                        TransferIssue(
                            level =
                                TransferIssueLevel
                                    .BLOCKING,
                            message =
                                "A matriz não possui pontos de bordado."
                        )
                    )
                }

                if (
                    design.bounds
                        .widthMm <=
                        0f ||
                    design.bounds
                        .heightMm <=
                        0f
                ) {
                    add(
                        TransferIssue(
                            level =
                                TransferIssueLevel
                                    .BLOCKING,
                            message =
                                "As dimensões da matriz são inválidas."
                        )
                    )
                }

                val fit =
                    HoopValidator
                        .validate(
                            design,
                            hoop
                        )

                if (
                    !fit.fits
                ) {
                    add(
                        TransferIssue(
                            level =
                                TransferIssueLevel
                                    .BLOCKING,
                            message =
                                "A matriz não cabe na área útil do bastidor ${hoop.displayName}."
                        )
                    )
                }

                if (
                    !design.endFound
                ) {
                    add(
                        TransferIssue(
                            level =
                                TransferIssueLevel
                                    .WARNING,
                            message =
                                "O arquivo original não informou comando END; o conversor preparará o arquivo de saída."
                        )
                    )
                }

                if (
                    design.colorCount >
                        64
                ) {
                    add(
                        TransferIssue(
                            level =
                                TransferIssueLevel
                                    .WARNING,
                            message =
                                "A matriz possui muitas trocas/blocos de cor. Confirme o limite da sua bordadeira."
                        )
                    )
                }
            }

        return MachineTransferValidation(
            format =
                normalizedFormat,
            hoop =
                hoop,
            issues =
                issues
        )
    }
}
