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

if (holedObjects.Count > 6)
{
    throw new InvalidOperationException(
        $"Smoke test: false junctions fragmented a simple holed shape into {holedObjects.Count} objects.");
}

var holedPoints = holedObjects
    .SelectMany(static item => item.Points)
    .ToList();

for (var i = 1; i < holedPoints.Count; i++)
{
    var previous = holedPoints[i - 1];
    var current = holedPoints[i];

    if (
        current.Command != StitchCommand.Stitch ||
        previous.ObjectIndex != current.ObjectIndex)
    {
        continue;
    }

    // No real stitch may cross the empty 40..80 square. This invariant is
    // valid whether the topology engine chooses Satin around the ring or
    // Tatami for a wider object.
    var samplesInsideHole = 0;

    for (var sample = 1; sample < 20; sample++)
    {
        var ratio = sample / 20f;
        var x = previous.X + (current.X - previous.X) * ratio;
        var y = previous.Y + (current.Y - previous.Y) * ratio;

        if (
            x > 42f &&
            x < 78f &&
            y > 42f &&
            y < 78f)
        {
            samplesInsideHole++;
        }
    }

    if (samplesInsideHole > 0)
    {
        var owner = holedObjects.First(item =>
            item.Index == current.ObjectIndex);

        throw new InvalidOperationException(
            $"Smoke test: {owner.Kind} object {owner.Index} stitched across hole: " +
            $"({previous.X:F2},{previous.Y:F2}) -> ({current.X:F2},{current.Y:F2}), " +
            $"command={current.Command}.");
    }
}

var holedTatami = holedObjects.FirstOrDefault(
    static item => item.Kind == EmbroideryObjectKind.Tatami);

if (holedTatami is not null)
{
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
    }
}

// Regression: a branched glyph must be digitized as independent local
// branches. Treating the whole connected shape as one Tatami object is the
// failure mode that produced the ladder/outline preview in Alliby.
using var branched = new SKPath();
branched.AddRect(new SKRect(46, 5, 66, 115));
branched.AddRect(new SKRect(12, 5, 100, 25));

var branchObjectIndex = 200;
var branchObjects = engine.DigitizePath(
    branched,
    options,
    ref branchObjectIndex);

var satinBranchCount = branchObjects.Count(static item =>
    item.Kind == EmbroideryObjectKind.Satin);

if (satinBranchCount < 2)
{
    throw new InvalidOperationException(
        $"Smoke test: expected multiple Satin branches, got {satinBranchCount}.");
}

if (branchObjects.Any(static item =>
    item.Kind == EmbroideryObjectKind.Tatami))
{
    throw new InvalidOperationException(
        "Smoke test: narrow branched script emitted an unnecessary Tatami junction patch.");
}

if (branchObjects.Count > 3)
{
    throw new InvalidOperationException(
        $"Smoke test: branched geometry fragmented into {branchObjects.Count} objects.");
}

// Regression: a compact detached ornament (such as Alliby's heart
// above the i) is an area fill, not a medial-axis branch graph.
using var compactOrnament = new SKPath();
compactOrnament.AddCircle(60f, 60f, 14f);

var compactObjectIndex = 300;
var compactObjects = engine.DigitizePath(
    compactOrnament,
    options,
    ref compactObjectIndex);

if (
    compactObjects.Count != 1 ||
    compactObjects[0].Kind != EmbroideryObjectKind.Tatami)
{
    throw new InvalidOperationException(
        $"Smoke test: compact ornament should be one Tatami object, got " +
        $"{compactObjects.Count} objects / " +
        $"{string.Join(",", compactObjects.Select(static item => item.Kind))}.");
}

if (compactObjects[0].Points.Count < 12)
{
    throw new InvalidOperationException(
        $"Smoke test: compact ornament fill is too coarse: " +
        $"{compactObjects[0].Points.Count} points.");
}

// Regression: a four-way crossing represents two physical stroke
// continuations. Pair both through-directions at the same junction.
using var crossing = new SKPath();
crossing.AddRect(new SKRect(50f, 5f, 70f, 115f));
crossing.AddRect(new SKRect(5f, 50f, 115f, 70f));

var crossingObjectIndex = 400;
var crossingObjects = engine.DigitizePath(
    crossing,
    options,
    ref crossingObjectIndex);

var crossingSatin = crossingObjects.Count(static item =>
    item.Kind == EmbroideryObjectKind.Satin);

if (crossingSatin != 2 || crossingObjects.Count != 2)
{
    throw new InvalidOperationException(
        $"Smoke test: four-way crossing should resolve to 2 Satin continuations, got " +
        $"{crossingObjects.Count} objects / {crossingSatin} Satin.");
}

// Regression: a simple bent Satin stroke must never generate an internal
// bow-tie/X from rail swapping at the bend.
using var bentStroke = new SKPath();
bentStroke.AddRect(new SKRect(18f, 18f, 38f, 105f));
bentStroke.AddRect(new SKRect(18f, 85f, 105f, 105f));

var bentObjectIndex = 500;
var bentObjects = engine.DigitizePath(
    bentStroke,
    options,
    ref bentObjectIndex);

foreach (var bentSatinObject in bentObjects.Where(static item =>
    item.Kind == EmbroideryObjectKind.Satin))
{
    var segments = new List<(StitchPoint A, StitchPoint B)>();

    for (var i = 1; i < bentSatinObject.Points.Count; i++)
    {
        var a = bentSatinObject.Points[i - 1];
        var b = bentSatinObject.Points[i];

        if (
            b.Command != StitchCommand.Stitch ||
            a.ObjectIndex != b.ObjectIndex)
        {
            continue;
        }

        segments.Add((a, b));
    }

    for (var i = 0; i < segments.Count; i++)
    {
        for (var j = i + 2; j < segments.Count; j++)
        {
            if (j == i + 1)
            {
                continue;
            }

            if (SegmentsIntersect(
                segments[i].A,
                segments[i].B,
                segments[j].A,
                segments[j].B))
            {
                throw new InvalidOperationException(
                    $"Smoke test: Satin self-crossed in simple bend: " +
                    $"segment {i} intersects {j}.");
            }
        }
    }
}

Console.WriteLine(
    $"OK: {objects.Count} base objects, {flattened.Count} base points. " +
    $"Branch topology emitted {satinBranchCount} Satin branches without junction patches. " +
    $"Compact ornaments fill as Tatami, 4-way crossings pair twice, and bent Satin has no rail crossover.");

static bool SegmentsIntersect(
    StitchPoint a,
    StitchPoint b,
    StitchPoint c,
    StitchPoint d)
{
    static float Cross(
        StitchPoint p,
        StitchPoint q,
        StitchPoint r) =>
        (q.X - p.X) * (r.Y - p.Y) -
        (q.Y - p.Y) * (r.X - p.X);

    var ab1 = Cross(a, b, c);
    var ab2 = Cross(a, b, d);
    var cd1 = Cross(c, d, a);
    var cd2 = Cross(c, d, b);

    const float epsilon = 0.001f;

    return
        ab1 * ab2 < -epsilon &&
        cd1 * cd2 < -epsilon;
}
