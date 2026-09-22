using SkiaSharp;

namespace FioLab2.Engine;

public enum StitchCommand
{
    Stitch,
    Jump
}

public enum EmbroideryKind
{
    Satin,
    Tatami
}

public readonly record struct StitchPoint(
    float X,
    float Y,
    StitchCommand Command,
    int ObjectIndex);

public sealed record EmbroideryObject(
    int Index,
    EmbroideryKind Kind,
    IReadOnlyList<StitchPoint> Points);

public sealed record DigitizeOptions(
    float FontSizePx = 180f,
    float RasterScale = 2.0f,
    float SatinRowSpacingPx = 2.8f,
    float TatamiRowSpacingPx = 4.0f,
    float MaxSatinWidthPx = 52f,
    float CompactFillMaxSizePx = 34f,
    bool IncludeUnderlay = false);

public sealed record DigitizeResult(
    IReadOnlyList<EmbroideryObject> Objects,
    IReadOnlyList<StitchPoint> Stitches,
    SKRect Bounds)
{
    public int StitchCount => Stitches.Count;

    public int JumpCount =>
        Stitches.Count(static point =>
            point.Command == StitchCommand.Jump);
}

internal readonly record struct PixelPoint(
    float X,
    float Y);

internal sealed class RasterGlyph
{
    public RasterGlyph(
        bool[] mask,
        int width,
        int height,
        float scale,
        SKRect worldBounds,
        int margin)
    {
        Mask = mask;
        Width = width;
        Height = height;
        Scale = scale;
        WorldBounds = worldBounds;
        Margin = margin;
    }

    public bool[] Mask { get; }

    public int Width { get; }

    public int Height { get; }

    public float Scale { get; }

    public SKRect WorldBounds { get; }

    public int Margin { get; }

    public SKPoint ToWorld(
        PixelPoint point) =>
        new(
            WorldBounds.Left +
            (point.X - Margin) / Scale,
            WorldBounds.Top +
            (point.Y - Margin) / Scale);

    public PixelPoint ToPixel(
        SKPoint point) =>
        new(
            (point.X - WorldBounds.Left) * Scale + Margin,
            (point.Y - WorldBounds.Top) * Scale + Margin);
}

internal sealed class Component
{
    public required bool[] Membership { get; init; }

    public required int CanvasWidth { get; init; }

    public required int CanvasHeight { get; init; }

    public required int MinX { get; init; }

    public required int MinY { get; init; }

    public required int MaxX { get; init; }

    public required int MaxY { get; init; }

    public required int PixelCount { get; init; }

    public int Width => MaxX - MinX + 1;

    public int Height => MaxY - MinY + 1;

    public bool Contains(
        int x,
        int y)
    {
        if (
            x < 0 ||
            y < 0 ||
            x >= CanvasWidth ||
            y >= CanvasHeight)
        {
            return false;
        }

        return Membership[y * CanvasWidth + x];
    }

    public bool Contains(
        PixelPoint point) =>
        Contains(
            (int)MathF.Round(point.X),
            (int)MathF.Round(point.Y));
}

internal sealed record GraphNode(
    int Id,
    PixelPoint Center,
    bool IsJunction,
    IReadOnlyList<int> Pixels);

internal sealed record GraphEdge(
    int Id,
    int StartNodeId,
    int EndNodeId,
    IReadOnlyList<PixelPoint> Points,
    float Length,
    bool IsLoop);

internal readonly record struct SatinRow(
    PixelPoint A,
    PixelPoint B);
