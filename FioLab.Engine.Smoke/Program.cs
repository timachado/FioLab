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

Console.WriteLine(
    $"OK: {objects.Count} objects, {flattened.Count} points. Object completion invariant preserved.");
