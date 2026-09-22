using SkiaSharp;

namespace FioLab2.Engine;

public sealed class FioLab2Engine
{
    public DigitizeResult Digitize(
        SKTypeface typeface,
        string text,
        DigitizeOptions? options = null)
    {
        options ??=
            new DigitizeOptions();

        if (string.IsNullOrWhiteSpace(text))
        {
            return Empty();
        }

        using var font =
            new SKFont(
                typeface,
                options.FontSizePx);

        var glyphs =
            font.GetGlyphs(
                text.AsSpan());

        var positions =
            font.GetGlyphPositions(
                text.AsSpan(),
                new SKPoint(0f, 0f));

        var objects =
            new List<EmbroideryObject>();

        var stitches =
            new List<StitchPoint>();

        var nextObjectIndex = 0;

        var count =
            Math.Min(
                glyphs.Length,
                positions.Length);

        for (var glyphIndex = 0;
             glyphIndex < count;
             glyphIndex++)
        {
            using var glyphPath =
                font.GetGlyphPath(
                    glyphs[glyphIndex]);

            if (
                glyphPath is null ||
                glyphPath.IsEmpty)
            {
                continue;
            }

            using var positioned =
                new SKPath();

            var matrix =
                SKMatrix.CreateTranslation(
                    positions[glyphIndex].X,
                    positions[glyphIndex].Y);

            glyphPath.Transform(
                matrix,
                positioned);

            var glyphObjects =
                DigitizePath(
                    positioned,
                    options,
                    ref nextObjectIndex);

            objects.AddRange(
                glyphObjects);

            foreach (var item in glyphObjects)
            {
                stitches.AddRange(
                    item.Points);
            }
        }

        return new DigitizeResult(
            objects,
            stitches,
            ComputeBounds(stitches));
    }

    public List<EmbroideryObject> DigitizePath(
        SKPath path,
        DigitizeOptions options,
        ref int nextObjectIndex)
    {
        if (path.IsEmpty)
        {
            return [];
        }

        var raster =
            Geometry.Rasterize(
                path,
                options);

        var components =
            Geometry.FindComponents(
                raster);

        var result =
            new List<EmbroideryObject>();

        foreach (var component in components)
        {
            if (
                IsCompactFill(
                    component,
                    raster,
                    options))
            {
                AppendTatami(
                    component,
                    raster,
                    options,
                    ref nextObjectIndex,
                    result);

                continue;
            }

            var distance =
                DistanceTransform.Compute(
                    component);

            var skeleton =
                Skeletonizer.Thin(
                    component);

            var graph =
                CenterlineGraph.Build(
                    component,
                    skeleton);

            var edges =
                CenterlineGraph.PruneMicroSpurs(
                    graph,
                    distance,
                    component)
                .OrderBy(edge =>
                    edge.Points.Min(
                        static point =>
                            point.X))
                .ThenBy(edge =>
                    edge.Points.Min(
                        static point =>
                            point.Y))
                .ToList();

            var nodes =
                graph.Nodes.ToDictionary(
                    static node =>
                        node.Id);

            var before =
                result.Count;

            foreach (var edge in edges)
            {
                if (
                    edge.Length /
                    raster.Scale <
                    2.0f)
                {
                    continue;
                }

                var objectIndex =
                    nextObjectIndex++;

                var points =
                    SatinDigitizer.Build(
                        edge,
                        nodes,
                        component,
                        raster,
                        distance,
                        objectIndex,
                        options);

                if (points.Count < 4)
                {
                    continue;
                }

                result.Add(
                    new EmbroideryObject(
                        objectIndex,
                        EmbroideryKind.Satin,
                        points));
            }

            if (result.Count == before)
            {
                AppendTatami(
                    component,
                    raster,
                    options,
                    ref nextObjectIndex,
                    result);
            }
        }

        return result;
    }

    private static bool IsCompactFill(
        Component component,
        RasterGlyph raster,
        DigitizeOptions options)
    {
        var width =
            component.Width /
            raster.Scale;

        var height =
            component.Height /
            raster.Scale;

        var maxDimension =
            MathF.Max(
                width,
                height);

        var minDimension =
            MathF.Max(
                1f,
                MathF.Min(
                    width,
                    height));

        var aspect =
            maxDimension /
            minDimension;

        var areaRatio =
            component.PixelCount /
            (float)(
                component.Width *
                component.Height);

        return
            maxDimension <=
                options.CompactFillMaxSizePx &&
            aspect <= 2.2f &&
            areaRatio >= 0.24f;
    }

    private static void AppendTatami(
        Component component,
        RasterGlyph raster,
        DigitizeOptions options,
        ref int nextObjectIndex,
        List<EmbroideryObject> result)
    {
        var objectIndex =
            nextObjectIndex++;

        var points =
            TatamiDigitizer.Build(
                component,
                raster,
                objectIndex,
                options);

        if (points.Count < 2)
        {
            return;
        }

        result.Add(
            new EmbroideryObject(
                objectIndex,
                EmbroideryKind.Tatami,
                points));
    }

    private static DigitizeResult Empty() =>
        new(
            [],
            [],
            SKRect.Empty);

    private static SKRect ComputeBounds(
        IReadOnlyList<StitchPoint> stitches)
    {
        if (stitches.Count == 0)
        {
            return SKRect.Empty;
        }

        return new SKRect(
            stitches.Min(
                static point =>
                    point.X),
            stitches.Min(
                static point =>
                    point.Y),
            stitches.Max(
                static point =>
                    point.X),
            stitches.Max(
                static point =>
                    point.Y));
    }
}
