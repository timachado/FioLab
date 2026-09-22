namespace FioLab2.Engine;

internal static class SatinDigitizer
{
    public static List<StitchPoint> Build(
        GraphEdge edge,
        IReadOnlyDictionary<int, GraphNode> nodes,
        Component component,
        RasterGlyph raster,
        float[] distance,
        int objectIndex,
        DigitizeOptions options)
    {
        var spacing =
            MathF.Max(
                2f,
                options.SatinRowSpacingPx *
                raster.Scale);

        var centers =
            PrepareCenterline(
                edge,
                nodes,
                component,
                distance,
                spacing);

        if (centers.Count < 2)
        {
            return [];
        }

        var rows =
            BuildRows(
                centers,
                component,
                raster,
                distance,
                options);

        if (rows.Count < 2)
        {
            return [];
        }

        return BuildStitches(
            rows,
            centers,
            component,
            raster,
            objectIndex,
            options);
    }

    private static List<PixelPoint> PrepareCenterline(
        GraphEdge edge,
        IReadOnlyDictionary<int, GraphNode> nodes,
        Component component,
        float[] distance,
        float spacing)
    {
        var smoothed =
            Smooth(
                edge.Points,
                component,
                passes: 2,
                closed: edge.IsLoop);

        var centers =
            Resample(
                smoothed,
                spacing);

        if (
            edge.IsLoop ||
            centers.Count < 4)
        {
            return centers;
        }

        var trimStart = 0f;
        var trimEnd = 0f;

        if (
            edge.StartNodeId >= 0 &&
            nodes.TryGetValue(
                edge.StartNodeId,
                out var startNode) &&
            startNode.IsJunction)
        {
            var radius =
                DistanceTransform.Sample(
                    distance,
                    component,
                    startNode.Center);

            trimStart =
                MathF.Max(
                    spacing * 0.75f,
                    radius * 0.30f);
        }

        if (
            edge.EndNodeId >= 0 &&
            nodes.TryGetValue(
                edge.EndNodeId,
                out var endNode) &&
            endNode.IsJunction)
        {
            var radius =
                DistanceTransform.Sample(
                    distance,
                    component,
                    endNode.Center);

            trimEnd =
                MathF.Max(
                    spacing * 0.75f,
                    radius * 0.30f);
        }

        return TrimByArcLength(
            centers,
            trimStart,
            trimEnd);
    }

    private static List<SatinRow> BuildRows(
        IReadOnlyList<PixelPoint> centers,
        Component component,
        RasterGlyph raster,
        float[] distance,
        DigitizeOptions options)
    {
        var rows =
            new List<SatinRow>();

        PixelPoint? previousNormal = null;

        var maxHalfWidth =
            options.MaxSatinWidthPx *
            raster.Scale *
            0.5f;

        for (var index = 0;
             index < centers.Count;
             index++)
        {
            var beforeIndex =
                Math.Max(
                    0,
                    index - 2);

            var afterIndex =
                Math.Min(
                    centers.Count - 1,
                    index + 2);

            if (beforeIndex == afterIndex)
            {
                continue;
            }

            var before =
                centers[beforeIndex];

            var after =
                centers[afterIndex];

            var tangent =
                Geometry.Normalize(
                    after.X - before.X,
                    after.Y - before.Y);

            var normal =
                new PixelPoint(
                    -tangent.Y,
                    tangent.X);

            if (previousNormal is not null)
            {
                var dot =
                    previousNormal.Value.X *
                    normal.X +
                    previousNormal.Value.Y *
                    normal.Y;

                if (dot < 0f)
                {
                    normal =
                        new PixelPoint(
                            -normal.X,
                            -normal.Y);
                }

                normal =
                    LimitNormalTurn(
                        previousNormal.Value,
                        normal,
                        MathF.PI * 0.18f);
            }

            var radius =
                DistanceTransform.Sample(
                    distance,
                    component,
                    centers[index]);

            var halfWidth =
                MathF.Min(
                    radius * 0.92f,
                    maxHalfWidth);

            if (halfWidth < 0.85f)
            {
                continue;
            }

            if (!TryCreateSafeRow(
                centers[index],
                normal,
                halfWidth,
                component,
                rows,
                out var row))
            {
                continue;
            }

            rows.Add(row);

            previousNormal =
                Geometry.Normalize(
                    row.B.X - row.A.X,
                    row.B.Y - row.A.Y);
        }

        return rows;
    }

    private static bool TryCreateSafeRow(
        PixelPoint center,
        PixelPoint normal,
        float halfWidth,
        Component component,
        IReadOnlyList<SatinRow> previousRows,
        out SatinRow row)
    {
        ReadOnlySpan<float> scales =
        [
            1.00f,
            0.92f,
            0.84f,
            0.76f,
            0.68f
        ];

        foreach (var scale in scales)
        {
            var width =
                halfWidth * scale;

            var candidate =
                new SatinRow(
                    new PixelPoint(
                        center.X -
                        normal.X * width,
                        center.Y -
                        normal.Y * width),
                    new PixelPoint(
                        center.X +
                        normal.X * width,
                        center.Y +
                        normal.Y * width));

            if (
                !component.Contains(
                    candidate.A) ||
                !component.Contains(
                    candidate.B) ||
                !Geometry.SegmentInside(
                    component,
                    candidate.A,
                    candidate.B))
            {
                continue;
            }

            var crosses =
                previousRows.Any(
                    previous =>
                        Geometry.ProperlyIntersects(
                            previous.A,
                            previous.B,
                            candidate.A,
                            candidate.B));

            if (crosses)
            {
                continue;
            }

            row = candidate;
            return true;
        }

        row = default;
        return false;
    }

    private static List<StitchPoint> BuildStitches(
        IReadOnlyList<SatinRow> rows,
        IReadOnlyList<PixelPoint> centers,
        Component component,
        RasterGlyph raster,
        int objectIndex,
        DigitizeOptions options)
    {
        var result =
            new List<StitchPoint>();

        if (options.IncludeUnderlay)
        {
            AppendUnderlay(
                centers,
                component,
                raster,
                objectIndex,
                result);
        }

        var emittedSegments =
            new List<(PixelPoint A, PixelPoint B)>();

        PixelPoint? previousEnd = null;

        for (var index = 0;
             index < rows.Count;
             index++)
        {
            var row = rows[index];

            var start =
                index % 2 == 0
                    ? row.A
                    : row.B;

            var end =
                index % 2 == 0
                    ? row.B
                    : row.A;

            var connectorIsSafe =
                previousEnd is not null &&
                Geometry.SegmentInside(
                    component,
                    previousEnd.Value,
                    start) &&
                !CrossesAny(
                    previousEnd.Value,
                    start,
                    emittedSegments);

            var startWorld =
                raster.ToWorld(start);

            var endWorld =
                raster.ToWorld(end);

            result.Add(
                new StitchPoint(
                    startWorld.X,
                    startWorld.Y,
                    connectorIsSafe
                        ? StitchCommand.Stitch
                        : StitchCommand.Jump,
                    objectIndex));

            if (
                connectorIsSafe &&
                previousEnd is not null)
            {
                emittedSegments.Add(
                    (previousEnd.Value, start));
            }

            result.Add(
                new StitchPoint(
                    endWorld.X,
                    endWorld.Y,
                    StitchCommand.Stitch,
                    objectIndex));

            emittedSegments.Add(
                (start, end));

            previousEnd = end;
        }

        return result;
    }

    private static void AppendUnderlay(
        IReadOnlyList<PixelPoint> centers,
        Component component,
        RasterGlyph raster,
        int objectIndex,
        List<StitchPoint> result)
    {
        PixelPoint? previous = null;

        foreach (var center in centers)
        {
            var world =
                raster.ToWorld(center);

            var command =
                previous is not null &&
                Geometry.SegmentInside(
                    component,
                    previous.Value,
                    center)
                    ? StitchCommand.Stitch
                    : StitchCommand.Jump;

            result.Add(
                new StitchPoint(
                    world.X,
                    world.Y,
                    command,
                    objectIndex));

            previous = center;
        }
    }

    private static bool CrossesAny(
        PixelPoint a,
        PixelPoint b,
        IReadOnlyList<(PixelPoint A, PixelPoint B)> segments)
    {
        foreach (var segment in segments)
        {
            if (
                Geometry.ProperlyIntersects(
                    a,
                    b,
                    segment.A,
                    segment.B))
            {
                return true;
            }
        }

        return false;
    }

    private static List<PixelPoint> Smooth(
        IReadOnlyList<PixelPoint> source,
        Component component,
        int passes,
        bool closed)
    {
        if (
            source.Count < 3 ||
            passes <= 0)
        {
            return source.ToList();
        }

        var current =
            source.ToList();

        for (var pass = 0;
             pass < passes;
             pass++)
        {
            var next =
                new List<PixelPoint>(
                    current.Count);

            for (var index = 0;
                 index < current.Count;
                 index++)
            {
                if (
                    !closed &&
                    (index == 0 ||
                     index == current.Count - 1))
                {
                    next.Add(
                        current[index]);

                    continue;
                }

                var previous =
                    current[
                        (index - 1 +
                         current.Count) %
                        current.Count];

                var point =
                    current[index];

                var following =
                    current[
                        (index + 1) %
                        current.Count];

                var smoothed =
                    new PixelPoint(
                        (
                            previous.X +
                            point.X * 2f +
                            following.X
                        ) / 4f,
                        (
                            previous.Y +
                            point.Y * 2f +
                            following.Y
                        ) / 4f);

                next.Add(
                    component.Contains(
                        smoothed)
                        ? smoothed
                        : point);
            }

            current = next;
        }

        return current;
    }

    private static List<PixelPoint> Resample(
        IReadOnlyList<PixelPoint> source,
        float spacing)
    {
        if (source.Count < 2)
        {
            return source.ToList();
        }

        var cumulative =
            new float[source.Count];

        for (var index = 1;
             index < source.Count;
             index++)
        {
            cumulative[index] =
                cumulative[index - 1] +
                Geometry.Distance(
                    source[index - 1],
                    source[index]);
        }

        var total =
            cumulative[^1];

        if (total <= spacing)
        {
            return
            [
                source[0],
                source[^1]
            ];
        }

        var result =
            new List<PixelPoint>();

        var sourceIndex = 1;

        for (var target = 0f;
             target < total;
             target += spacing)
        {
            while (
                sourceIndex <
                    cumulative.Length - 1 &&
                cumulative[sourceIndex] <
                    target)
            {
                sourceIndex++;
            }

            var previousIndex =
                Math.Max(
                    0,
                    sourceIndex - 1);

            var segmentStart =
                cumulative[previousIndex];

            var segmentEnd =
                cumulative[sourceIndex];

            var denominator =
                MathF.Max(
                    0.0001f,
                    segmentEnd - segmentStart);

            var ratio =
                Math.Clamp(
                    (target - segmentStart) /
                    denominator,
                    0f,
                    1f);

            var a =
                source[previousIndex];

            var b =
                source[sourceIndex];

            result.Add(
                new PixelPoint(
                    a.X +
                    (b.X - a.X) * ratio,
                    a.Y +
                    (b.Y - a.Y) * ratio));
        }

        if (
            Geometry.Distance(
                result[^1],
                source[^1]) >
            spacing * 0.35f)
        {
            result.Add(
                source[^1]);
        }

        return result;
    }

    private static List<PixelPoint> TrimByArcLength(
        IReadOnlyList<PixelPoint> source,
        float trimStart,
        float trimEnd)
    {
        if (
            source.Count < 3 ||
            (trimStart <= 0f &&
             trimEnd <= 0f))
        {
            return source.ToList();
        }

        var cumulative =
            new float[source.Count];

        for (var index = 1;
             index < source.Count;
             index++)
        {
            cumulative[index] =
                cumulative[index - 1] +
                Geometry.Distance(
                    source[index - 1],
                    source[index]);
        }

        var total =
            cumulative[^1];

        var from =
            Math.Clamp(
                trimStart,
                0f,
                total * 0.40f);

        var to =
            Math.Clamp(
                total - trimEnd,
                total * 0.60f,
                total);

        var result =
            source
                .Where((point, index) =>
                    cumulative[index] >= from &&
                    cumulative[index] <= to)
                .ToList();

        return result.Count >= 2
            ? result
            : source.ToList();
    }

    private static PixelPoint LimitNormalTurn(
        PixelPoint previous,
        PixelPoint current,
        float maximumRadians)
    {
        var previousVector =
            Geometry.Normalize(
                previous.X,
                previous.Y);

        var currentVector =
            Geometry.Normalize(
                current.X,
                current.Y);

        var dot =
            Math.Clamp(
                previousVector.X *
                currentVector.X +
                previousVector.Y *
                currentVector.Y,
                -1f,
                1f);

        var angle =
            MathF.Acos(dot);

        if (angle <= maximumRadians)
        {
            return currentVector;
        }

        var cross =
            previousVector.X *
            currentVector.Y -
            previousVector.Y *
            currentVector.X;

        var signed =
            cross >= 0f
                ? maximumRadians
                : -maximumRadians;

        var cosine =
            MathF.Cos(signed);

        var sine =
            MathF.Sin(signed);

        return new PixelPoint(
            previousVector.X * cosine -
            previousVector.Y * sine,
            previousVector.X * sine +
            previousVector.Y * cosine);
    }
}
