using FioLab2.Engine;
using SkiaSharp;

var engine = new FioLab2Engine();

var options = new DigitizeOptions(
    RasterScale: 2.0f,
    SatinRowSpacingPx: 2.6f,
    TatamiRowSpacingPx: 3.8f,
    MaxSatinWidthPx: 52f,
    CompactFillMaxSizePx: 34f,
    IncludeUnderlay: false);

RunStraight(engine, options);
RunDiagonal(engine, options);
RunBent(engine, options);
RunConcaveU(engine, options);
RunTee(engine, options);
RunLoop(engine, options);
RunCompactFill(engine, options);

Console.WriteLine(
    "OK: FioLab V2 core passed straight, diagonal, bend, concave-U, T-junction, loop and compact-fill tests.");

static void RunStraight(
    FioLab2Engine engine,
    DigitizeOptions options)
{
    using var path = new SKPath();
    path.AddRect(
        new SKRect(
            30f,
            10f,
            54f,
            150f));

    var objects =
        Digitize(
            engine,
            path,
            options);

    var satin =
        objects.Where(static item =>
            item.Kind == EmbroideryKind.Satin)
        .ToList();

    Require(
        satin.Count == 1,
        $"straight ribbon: expected 1 Satin object, got {satin.Count}.");

    Require(
        satin[0].Points.Count >= 20,
        $"straight ribbon: too sparse ({satin[0].Points.Count} points).");

    Require(
        CountJumps(satin[0]) <= 2,
        $"straight ribbon: too many jumps ({CountJumps(satin[0])}).");

    AssertNoSelfCrossing(
        satin[0],
        "straight ribbon");
}

static void RunDiagonal(
    FioLab2Engine engine,
    DigitizeOptions options)
{
    using var source = new SKPath();

    source.AddRect(
        new SKRect(
            56f,
            12f,
            80f,
            158f));

    using var path = new SKPath();

    var rotation =
        SKMatrix.CreateRotationDegrees(
            31f,
            68f,
            85f);

    source.Transform(
        rotation,
        path);

    var objects =
        Digitize(
            engine,
            path,
            options);

    var satin =
        objects.Where(static item =>
            item.Kind == EmbroideryKind.Satin)
        .ToList();

    Require(
        satin.Count <= 2,
        $"diagonal ribbon: raster staircase fragmented into {satin.Count} Satin objects.");

    Require(
        satin.Sum(static item => item.Points.Count) >= 20,
        "diagonal ribbon: result became too sparse.");

    foreach (var item in satin)
    {
        Require(
            CountJumps(item) <= 2,
            $"diagonal ribbon: object {item.Index} has {CountJumps(item)} jumps.");

        AssertNoSelfCrossing(
            item,
            "diagonal ribbon");
    }
}

static void RunBent(
    FioLab2Engine engine,
    DigitizeOptions options)
{
    using var path = new SKPath();

    path.AddRect(
        new SKRect(
            20f,
            15f,
            44f,
            125f));

    path.AddRect(
        new SKRect(
            20f,
            101f,
            132f,
            125f));

    var objects =
        Digitize(
            engine,
            path,
            options);

    var satin =
        objects.Where(static item =>
            item.Kind == EmbroideryKind.Satin)
        .ToList();

    Require(
        satin.Count is >= 1 and <= 4,
        $"L bend: expected 1..4 Satin objects, got {satin.Count}.");

    foreach (var item in satin)
    {
        Require(
            item.Points.Count >= 8,
            $"L bend: sparse Satin object {item.Index} ({item.Points.Count} points).");

        Require(
            CountJumps(item) <= 3,
            $"L bend: object {item.Index} fragmented into {CountJumps(item)} jumps.");

        AssertNoSelfCrossing(
            item,
            "L bend");
    }
}

static void RunConcaveU(
    FioLab2Engine engine,
    DigitizeOptions options)
{
    using var path = new SKPath();

    path.AddRect(
        new SKRect(
            18f,
            18f,
            42f,
            146f));

    path.AddRect(
        new SKRect(
            102f,
            18f,
            126f,
            146f));

    path.AddRect(
        new SKRect(
            18f,
            122f,
            126f,
            146f));

    var objects =
        Digitize(
            engine,
            path,
            options);

    var satin =
        objects.Where(static item =>
            item.Kind == EmbroideryKind.Satin)
        .ToList();

    Require(
        satin.Count <= 3,
        $"concave U: fragmented into {satin.Count} Satin objects.");

    var totalJumps =
        satin.Sum(CountJumps);

    Require(
        totalJumps <= satin.Count + 2,
        $"concave U: too many travels ({totalJumps} jumps across {satin.Count} objects).");

    Require(
        satin.Sum(static item => item.Points.Count) >= 30,
        "concave U: result became too sparse.");

    foreach (var item in satin)
    {
        AssertNoSelfCrossing(
            item,
            "concave U");
    }
}

static void RunTee(
    FioLab2Engine engine,
    DigitizeOptions options)
{
    using var path = new SKPath();

    path.AddRect(
        new SKRect(
            54f,
            20f,
            78f,
            150f));

    path.AddRect(
        new SKRect(
            15f,
            20f,
            117f,
            44f));

    var objects =
        Digitize(
            engine,
            path,
            options);

    var satin =
        objects.Where(static item =>
            item.Kind == EmbroideryKind.Satin)
        .ToList();

    Require(
        satin.Count is >= 2 and <= 6,
        $"T junction: expected independent Satin branches, got {satin.Count}.");

    foreach (var item in satin)
    {
        AssertNoSelfCrossing(
            item,
            "T junction");
    }
}

static void RunLoop(
    FioLab2Engine engine,
    DigitizeOptions options)
{
    using var path = new SKPath
    {
        FillType = SKPathFillType.EvenOdd
    };

    path.AddCircle(
        70f,
        70f,
        48f);

    path.AddCircle(
        70f,
        70f,
        29f);

    var objects =
        Digitize(
            engine,
            path,
            options);

    var satin =
        objects.Where(static item =>
            item.Kind == EmbroideryKind.Satin)
        .ToList();

    Require(
        satin.Count >= 1,
        "loop: expected at least one Satin loop.");

    foreach (var item in satin)
    {
        AssertNoSelfCrossing(
            item,
            "loop");
    }
}

static void RunCompactFill(
    FioLab2Engine engine,
    DigitizeOptions options)
{
    using var path = new SKPath();

    path.AddCircle(
        30f,
        30f,
        12f);

    var objects =
        Digitize(
            engine,
            path,
            options);

    Require(
        objects.Count == 1 &&
        objects[0].Kind == EmbroideryKind.Tatami,
        $"compact fill: expected one Tatami object, got {objects.Count} / " +
        $"{string.Join(",", objects.Select(static item => item.Kind))}.");

    Require(
        objects[0].Points.Count >= 8,
        $"compact fill: too sparse ({objects[0].Points.Count} points).");
}

static List<EmbroideryObject> Digitize(
    FioLab2Engine engine,
    SKPath path,
    DigitizeOptions options)
{
    var nextObjectIndex = 0;

    var objects =
        engine.DigitizePath(
            path,
            options,
            ref nextObjectIndex);

    Require(
        objects.Count > 0,
        "digitizer returned no objects.");

    return objects;
}

static int CountJumps(
    EmbroideryObject item) =>
    item.Points.Count(static point =>
        point.Command == StitchCommand.Jump);

static void AssertNoSelfCrossing(
    EmbroideryObject item,
    string name)
{
    var segments =
        new List<(StitchPoint A, StitchPoint B)>();

    for (var index = 1;
         index < item.Points.Count;
         index++)
    {
        var previous =
            item.Points[index - 1];

        var current =
            item.Points[index];

        if (
            current.Command !=
                StitchCommand.Stitch ||
            previous.ObjectIndex !=
                current.ObjectIndex)
        {
            continue;
        }

        segments.Add(
            (previous, current));
    }

    for (var first = 0;
         first < segments.Count;
         first++)
    {
        for (var second = first + 2;
             second < segments.Count;
             second++)
        {
            if (
                ProperlyIntersects(
                    segments[first].A,
                    segments[first].B,
                    segments[second].A,
                    segments[second].B))
            {
                var firstSegment =
                    segments[first];

                var secondSegment =
                    segments[second];

                throw new InvalidOperationException(
                    $"{name}: Satin object {item.Index} self-crossed " +
                    $"at stitch segments {first} and {second}. " +
                    $"S{first}=({firstSegment.A.X:F2},{firstSegment.A.Y:F2})->" +
                    $"({firstSegment.B.X:F2},{firstSegment.B.Y:F2}); " +
                    $"S{second}=({secondSegment.A.X:F2},{secondSegment.A.Y:F2})->" +
                    $"({secondSegment.B.X:F2},{secondSegment.B.Y:F2}).");
            }
        }
    }
}

static bool ProperlyIntersects(
    StitchPoint a,
    StitchPoint b,
    StitchPoint c,
    StitchPoint d)
{
    static float Cross(
        StitchPoint p,
        StitchPoint q,
        StitchPoint r) =>
        (q.X - p.X) *
        (r.Y - p.Y) -
        (q.Y - p.Y) *
        (r.X - p.X);

    var ab1 = Cross(a, b, c);
    var ab2 = Cross(a, b, d);
    var cd1 = Cross(c, d, a);
    var cd2 = Cross(c, d, b);

    const float epsilon = 0.0001f;

    return
        ab1 * ab2 < -epsilon &&
        cd1 * cd2 < -epsilon;
}

static void Require(
    bool condition,
    string message)
{
    if (!condition)
    {
        throw new InvalidOperationException(
            message);
    }
}
