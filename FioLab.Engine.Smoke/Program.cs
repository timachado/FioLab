using FioLab.Engine;
using SkiaSharp;

var engine = new SkiaEmbroideryEngine();
var options = new DigitizeOptions(
    FontSizePx: 180f,
    RasterScale: 1.4f,
    DensityPx: 3f,
    TatamiDensityPx: 4f,
    StitchLengthPx: 12f,
    RunningMaxWidthPx: 4f,
    MaxSatinWidthPx: 40f,
    IncludeUnderlay: true);

var nextObject = 0;

using var synthetic = new SKPath();

// Two independent solid regions force two embroidery objects.
synthetic.AddRect(new SKRect(0, 0, 28, 130));
synthetic.AddRect(new SKRect(55, 10, 125, 100));

var objects = engine.DigitizePath(
    synthetic,
    options,
    ref nextObject);

if (objects.Count < 2)
{
    throw new InvalidOperationException(
        "Smoke test: expected at least two independent embroidery objects.");
}

var flattened = objects
    .SelectMany(static item => item.Points)
    .ToList();

for (var objectIndex = 0; objectIndex < objects.Count; objectIndex++)
{
    var positions = flattened
        .Select((point, index) => (point, index))
        .Where(tuple => tuple.point.ObjectIndex == objects[objectIndex].Index)
        .Select(static tuple => tuple.index)
        .ToArray();

    if (positions.Length == 0)
    {
        throw new InvalidOperationException(
            "Smoke test: object emitted no points.");
    }

    var first = positions[0];
    var last = positions[^1];

    for (var i = first; i <= last; i++)
    {
        if (flattened[i].ObjectIndex != objects[objectIndex].Index)
        {
            throw new InvalidOperationException(
                "Smoke test: embroidery object was interrupted by another object.");
        }
    }
}

var satin = objects.FirstOrDefault(
    static item => item.Kind == EmbroideryObjectKind.Satin);

if (satin is not null)
{
    var satinStitches = satin.Points
        .Where(static point => point.Command == StitchCommand.Stitch)
        .ToArray();

    if (satinStitches.Length < 4)
    {
        throw new InvalidOperationException(
            "Smoke test: satin object did not emit enough stitches.");
    }
}

// Regression: a Tatami scan row may contain more than one span (for
// example around a hole). Every span on that row must keep the same
// travel direction, and the empty gap must be crossed with needle-up.
using var holed = new SKPath
{
    FillType = SKPathFillType.EvenOdd
};

holed.AddRect(new SKRect(0, 0, 120, 120));
holed.AddRect(new SKRect(40, 40, 80, 80));

var holeObjectIndex = 100;
var holedObjects = engine.DigitizePath(
    holed,
    options,
    ref holeObjectIndex);

var holedTatami = holedObjects
    .FirstOrDefault(static item =>
        item.Kind == EmbroideryObjectKind.Tatami);

if (holedTatami is null)
{
    throw new InvalidOperationException(
        "Smoke test: holed object did not produce Tatami.");
}

var directionByRow = new Dictionary<int, int>();

for (var i = 1; i < holedTatami.Points.Count; i++)
{
    var previous = holedTatami.Points[i - 1];
    var current = holedTatami.Points[i];

    if (
        current.Command != StitchCommand.Stitch ||
        previous.ObjectIndex != current.ObjectIndex)
    {
        continue;
    }

    var dx = current.X - previous.X;
    var dy = current.Y - previous.Y;

    if (MathF.Abs(dy) > 0.01f || MathF.Abs(dx) <= 0.01f)
    {
        continue;
    }

    var rowKey = (int)MathF.Round(current.Y * 10f);
    var direction = Math.Sign(dx);

    if (
        directionByRow.TryGetValue(rowKey, out var existingDirection) &&
        existingDirection != direction)
    {
        throw new InvalidOperationException(
            "Smoke test: Tatami reversed direction before completing the scan row.");
    }

    directionByRow[rowKey] = direction;

    var minX = MathF.Min(previous.X, current.X);
    var maxX = MathF.Max(previous.X, current.X);
    var rowY = (previous.Y + current.Y) / 2f;

    if (
        rowY > 40f &&
        rowY < 80f &&
        minX < 40f &&
        maxX > 80f)
    {
        throw new InvalidOperationException(
            "Smoke test: Tatami stitched across an empty internal gap.");
    }
}

Console.WriteLine(
    $"OK: {objects.Count} objects, {flattened.Count} points. Object completion invariant preserved. Tatami row continuity preserved.");
