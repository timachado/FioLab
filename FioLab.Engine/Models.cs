using SkiaSharp;

namespace FioLab.Engine;

public enum StitchCommand
{
    Jump,
    Stitch
}

public enum EmbroideryObjectKind
{
    Running,
    Satin,
    Tatami
}

public readonly record struct StitchPoint(
    float X,
    float Y,
    StitchCommand Command,
    int ObjectIndex);

public sealed class EmbroideryObject
{
    public required int Index { get; init; }
    public required EmbroideryObjectKind Kind { get; init; }
    public required List<StitchPoint> Points { get; init; }
}

public sealed class DigitizeResult
{
    public required List<EmbroideryObject> Objects { get; init; }
    public required List<StitchPoint> Stitches { get; init; }
    public required SKRect Bounds { get; init; }

    public int StitchCount =>
        Stitches.Count(static point => point.Command == StitchCommand.Stitch);
}

public sealed record DigitizeOptions(
    float FontSizePx = 180f,
    float RasterScale = 1.5f,
    float DensityPx = 3.2f,
    float TatamiDensityPx = 4.2f,
    float StitchLengthPx = 13f,
    float RunningMaxWidthPx = 4f,
    float MaxSatinWidthPx = 42f,
    bool IncludeUnderlay = true);
