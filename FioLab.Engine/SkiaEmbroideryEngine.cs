using SkiaSharp;

namespace FioLab.Engine;

public sealed class SkiaEmbroideryEngine
{
    public DigitizeResult Digitize(
        SKTypeface typeface,
        string text,
        DigitizeOptions? options = null)
    {
        options ??= new DigitizeOptions();

        if (string.IsNullOrWhiteSpace(text))
        {
            return Empty();
        }

        using var font = new SKFont(typeface, options.FontSizePx);

        var glyphs = font.GetGlyphs(text.AsSpan());
        var positions = font.GetGlyphPositions(text.AsSpan(), new SKPoint(0f, 0f));

        var objects = new List<EmbroideryObject>();
        var stitches = new List<StitchPoint>();
        var objectIndex = 0;

        var count = Math.Min(glyphs.Length, positions.Length);

        for (var i = 0; i < count; i++)
        {
            using var glyphPath = font.GetGlyphPath(glyphs[i]);

            if (glyphPath is null || glyphPath.IsEmpty)
            {
                continue;
            }

            using var positioned = new SKPath();
            var matrix = SKMatrix.CreateTranslation(
                positions[i].X,
                positions[i].Y);

            glyphPath.Transform(matrix, positioned);

            var glyphObjects = DigitizePath(
                positioned,
                options,
                ref objectIndex);

            foreach (var embroideryObject in glyphObjects)
            {
                objects.Add(embroideryObject);
                stitches.AddRange(embroideryObject.Points);
            }
        }

        return new DigitizeResult
        {
            Objects = objects,
            Stitches = stitches,
            Bounds = ComputeBounds(stitches)
        };
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

        using var raster = Rasterize(path, options);
        var components = FindComponents(raster.Mask, raster.Width, raster.Height);

        var result = new List<EmbroideryObject>();

        foreach (var component in components)
        {
            var objectIndex = nextObjectIndex++;
            var kind = Classify(component, raster.Scale, options);

            var points = kind switch
            {
                EmbroideryObjectKind.Running =>
                    BuildRunning(component, raster, objectIndex, options),

                EmbroideryObjectKind.Satin =>
                    BuildSatin(component, raster, objectIndex, options),

                _ =>
                    BuildTatami(component, raster, objectIndex, options)
            };

            if (points.Count < 2)
            {
                continue;
            }

            result.Add(new EmbroideryObject
            {
                Index = objectIndex,
                Kind = kind,
                Points = points
            });
        }

        return result;
    }

    private static DigitizeResult Empty() =>
        new()
        {
            Objects = [],
            Stitches = [],
            Bounds = SKRect.Empty
        };

    private static SKRect ComputeBounds(IReadOnlyList<StitchPoint> stitches)
    {
        if (stitches.Count == 0)
        {
            return SKRect.Empty;
        }

        var left = stitches.Min(static point => point.X);
        var top = stitches.Min(static point => point.Y);
        var right = stitches.Max(static point => point.X);
        var bottom = stitches.Max(static point => point.Y);

        return new SKRect(left, top, right, bottom);
    }

    private static EmbroideryObjectKind Classify(
        Component component,
        float scale,
        DigitizeOptions options)
    {
        var widthWorld = component.Width / scale;
        var heightWorld = component.Height / scale;
        var minDimension = MathF.Min(widthWorld, heightWorld);
        var maxDimension = MathF.Max(widthWorld, heightWorld);
        var aspect = maxDimension / MathF.Max(1f, minDimension);

        if (minDimension <= options.RunningMaxWidthPx && aspect >= 2.0f)
        {
            return EmbroideryObjectKind.Running;
        }

        if (minDimension <= options.MaxSatinWidthPx && aspect >= 1.35f)
        {
            return EmbroideryObjectKind.Satin;
        }

        return EmbroideryObjectKind.Tatami;
    }

    private static RasterGlyph Rasterize(
        SKPath path,
        DigitizeOptions options)
    {
        var bounds = path.Bounds;
        var scale = options.RasterScale.Clamp(1f, 3f);
        var margin = 4;

        var width = Math.Max(
            8,
            (int)MathF.Ceiling(bounds.Width * scale) + margin * 2);

        var height = Math.Max(
            8,
            (int)MathF.Ceiling(bounds.Height * scale) + margin * 2);

        using var bitmap = new SKBitmap(
            width,
            height,
            SKColorType.Rgba8888,
            SKAlphaType.Premul);

        using (var canvas = new SKCanvas(bitmap))
        using (var paint = new SKPaint
        {
            Color = SKColors.White,
            Style = SKPaintStyle.Fill,
            IsAntialias = false
        })
        {
            canvas.Clear(SKColors.Transparent);
            canvas.Scale(scale, scale);
            canvas.Translate(
                -bounds.Left + margin / scale,
                -bounds.Top + margin / scale);

            canvas.DrawPath(path, paint);
            canvas.Flush();
        }

        var mask = new bool[width * height];

        for (var y = 0; y < height; y++)
        {
            for (var x = 0; x < width; x++)
            {
                mask[y * width + x] =
                    bitmap.GetPixel(x, y).Alpha >= 128;
            }
        }

        return new RasterGlyph(
            mask,
            width,
            height,
            scale,
            bounds.Left - margin / scale,
            bounds.Top - margin / scale);
    }

    private static List<Component> FindComponents(
        bool[] mask,
        int width,
        int height)
    {
        var visited = new bool[mask.Length];
        var result = new List<Component>();

        for (var seed = 0; seed < mask.Length; seed++)
        {
            if (!mask[seed] || visited[seed])
            {
                continue;
            }

            var queue = new Queue<int>();
            var pixels = new List<int>();

            queue.Enqueue(seed);
            visited[seed] = true;

            var minX = width;
            var minY = height;
            var maxX = 0;
            var maxY = 0;

            while (queue.Count > 0)
            {
                var index = queue.Dequeue();
                pixels.Add(index);

                var x = index % width;
                var y = index / width;

                minX = Math.Min(minX, x);
                minY = Math.Min(minY, y);
                maxX = Math.Max(maxX, x);
                maxY = Math.Max(maxY, y);

                Visit(x - 1, y);
                Visit(x + 1, y);
                Visit(x, y - 1);
                Visit(x, y + 1);
            }

            if (pixels.Count >= 2)
            {
                var componentMask = new bool[mask.Length];

                foreach (var index in pixels)
                {
                    componentMask[index] = true;
                }

                result.Add(new Component(
                    pixels,
                    componentMask,
                    minX,
                    minY,
                    maxX,
                    maxY,
                    width,
                    height));
            }

            continue;

            void Visit(int x, int y)
            {
                if (x < 0 || y < 0 || x >= width || y >= height)
                {
                    return;
                }

                var next = y * width + x;

                if (!mask[next] || visited[next])
                {
                    return;
                }

                visited[next] = true;
                queue.Enqueue(next);
            }
        }

        return result;
    }

    private static List<StitchPoint> BuildRunning(
        Component component,
        RasterGlyph raster,
        int objectIndex,
        DigitizeOptions options)
    {
        var geometry = ComputePrincipalGeometry(component);
        var step = MathF.Max(
            2f,
            options.StitchLengthPx * raster.Scale * 0.8f);

        var points = new List<StitchPoint>();
        var first = true;

        for (var t = geometry.MinT; t <= geometry.MaxT + 0.01f; t += step)
        {
            var candidate = FindNearestInsidePoint(
                component,
                geometry.Cx + geometry.AxisX * t,
                geometry.Cy + geometry.AxisY * t,
                geometry.NormalX,
                geometry.NormalY);

            if (candidate is null)
            {
                continue;
            }

            var world = raster.ToWorld(candidate.Value.X, candidate.Value.Y);

            points.Add(new StitchPoint(
                world.X,
                world.Y,
                first ? StitchCommand.Jump : StitchCommand.Stitch,
                objectIndex));

            first = false;
        }

        return points;
    }

    private static List<StitchPoint> BuildSatin(
        Component component,
        RasterGlyph raster,
        int objectIndex,
        DigitizeOptions options)
    {
        var geometry = ComputePrincipalGeometry(component);

        var pitch = MathF.Max(
            2f,
            options.DensityPx * raster.Scale);

        var rows = BuildSatinRows(
            component,
            geometry,
            pitch);

        if (rows.Count < 2)
        {
            return BuildTatami(
                component,
                raster,
                objectIndex,
                options);
        }

        var points = new List<StitchPoint>();

        if (options.IncludeUnderlay)
        {
            var centers = rows
                .Select(static row => new PixelPoint(
                    (row.A.X + row.B.X) / 2f,
                    (row.A.Y + row.B.Y) / 2f))
                .ToList();

            AppendPolyline(
                centers,
                raster,
                objectIndex,
                points,
                options.StitchLengthPx * raster.Scale,
                jumpAtStart: true);
        }

        var firstFill = true;

        for (var i = 0; i < rows.Count; i++)
        {
            var row = rows[i];

            var start = i % 2 == 0
                ? row.A
                : row.B;

            var end = i % 2 == 0
                ? row.B
                : row.A;

            var startWorld = raster.ToWorld(start.X, start.Y);
            var endWorld = raster.ToWorld(end.X, end.Y);

            if (firstFill)
            {
                points.Add(new StitchPoint(
                    startWorld.X,
                    startWorld.Y,
                    StitchCommand.Jump,
                    objectIndex));

                firstFill = false;
            }
            else
            {
                // Move only along the same physical rail before crossing back.
                points.Add(new StitchPoint(
                    startWorld.X,
                    startWorld.Y,
                    StitchCommand.Stitch,
                    objectIndex));
            }

            // Satin crossing is atomic: one edge -> the opposite edge.
            points.Add(new StitchPoint(
                endWorld.X,
                endWorld.Y,
                StitchCommand.Stitch,
                objectIndex));
        }

        return points;
    }

    private static List<SatinRow> BuildSatinRows(
        Component component,
        PrincipalGeometry geometry,
        float pitch)
    {
        var rows = new List<SatinRow>();
        SatinRow? previous = null;

        var normalExtent = MathF.Sqrt(
            component.Width * component.Width +
            component.Height * component.Height) + 4f;

        for (var t = geometry.MinT; t <= geometry.MaxT + 0.01f; t += pitch)
        {
            var cx = geometry.Cx + geometry.AxisX * t;
            var cy = geometry.Cy + geometry.AxisY * t;

            var intervals = SampleIntervalsOnNormal(
                component,
                cx,
                cy,
                geometry.NormalX,
                geometry.NormalY,
                normalExtent);

            if (intervals.Count == 0)
            {
                continue;
            }

            var selected = SelectInterval(
                intervals,
                previous,
                cx,
                cy,
                geometry.NormalX,
                geometry.NormalY);

            var candidate = new SatinRow(
                new PixelPoint(
                    cx + geometry.NormalX * selected.Start,
                    cy + geometry.NormalY * selected.Start),
                new PixelPoint(
                    cx + geometry.NormalX * selected.End,
                    cy + geometry.NormalY * selected.End));

            if (previous is not null)
            {
                var direct =
                    Distance(previous.Value.A, candidate.A) +
                    Distance(previous.Value.B, candidate.B);

                var swapped =
                    Distance(previous.Value.A, candidate.B) +
                    Distance(previous.Value.B, candidate.A);

                if (swapped < direct)
                {
                    candidate = new SatinRow(
                        candidate.B,
                        candidate.A);
                }
            }

            rows.Add(candidate);
            previous = candidate;
        }

        return rows;
    }

    private static Interval SelectInterval(
        IReadOnlyList<Interval> intervals,
        SatinRow? previous,
        float cx,
        float cy,
        float nx,
        float ny)
    {
        if (previous is null)
        {
            return intervals
                .OrderByDescending(static interval => interval.Length)
                .First();
        }

        var previousCenter = new PixelPoint(
            (previous.Value.A.X + previous.Value.B.X) / 2f,
            (previous.Value.A.Y + previous.Value.B.Y) / 2f);

        var previousS =
            (previousCenter.X - cx) * nx +
            (previousCenter.Y - cy) * ny;

        return intervals
            .OrderBy(interval =>
                MathF.Abs(
                    (interval.Start + interval.End) / 2f -
                    previousS))
            .ThenByDescending(static interval => interval.Length)
            .First();
    }

    private static List<Interval> SampleIntervalsOnNormal(
        Component component,
        float cx,
        float cy,
        float nx,
        float ny,
        float extent)
    {
        var result = new List<Interval>();

        var inRun = false;
        var runStart = 0f;
        var lastInside = 0f;

        for (var s = -extent; s <= extent; s += 1f)
        {
            var x = (int)MathF.Round(cx + nx * s);
            var y = (int)MathF.Round(cy + ny * s);
            var inside = component.Contains(x, y);

            if (inside && !inRun)
            {
                inRun = true;
                runStart = s;
            }

            if (inside)
            {
                lastInside = s;
            }

            if (!inside && inRun)
            {
                result.Add(new Interval(runStart, lastInside));
                inRun = false;
            }
        }

        if (inRun)
        {
            result.Add(new Interval(runStart, lastInside));
        }

        return result
            .Where(static interval => interval.Length >= 1f)
            .ToList();
    }

    private static List<StitchPoint> BuildTatami(
        Component component,
        RasterGlyph raster,
        int objectIndex,
        DigitizeOptions options)
    {
        var points = new List<StitchPoint>();

        var rowStep = Math.Max(
            2,
            (int)MathF.Round(
                options.TatamiDensityPx * raster.Scale));

        if (options.IncludeUnderlay)
        {
            var underlayStep = rowStep * 3;
            var underlayForward = true;

            for (var y = component.MinY; y <= component.MaxY; y += underlayStep)
            {
                var runs = FindRuns(component, y);

                foreach (var run in runs)
                {
                    AppendRun(
                        run,
                        y,
                        underlayForward,
                        raster,
                        objectIndex,
                        points,
                        options.StitchLengthPx * raster.Scale * 1.5f,
                        jumpAtStart: true);

                    underlayForward = !underlayForward;
                }
            }
        }

        var forward = true;
        var firstFill = true;

        for (var y = component.MinY; y <= component.MaxY; y += rowStep)
        {
            var runs = FindRuns(component, y);

            foreach (var run in runs)
            {
                AppendRun(
                    run,
                    y,
                    forward,
                    raster,
                    objectIndex,
                    points,
                    options.StitchLengthPx * raster.Scale,
                    jumpAtStart: firstFill);

                firstFill = false;
                forward = !forward;
            }
        }

        return points;
    }

    private static List<Run> FindRuns(
        Component component,
        int y)
    {
        var runs = new List<Run>();
        var x = component.MinX;

        while (x <= component.MaxX)
        {
            while (
                x <= component.MaxX &&
                !component.Contains(x, y))
            {
                x++;
            }

            if (x > component.MaxX)
            {
                break;
            }

            var start = x;

            while (
                x <= component.MaxX &&
                component.Contains(x, y))
            {
                x++;
            }

            var end = x - 1;

            if (end > start)
            {
                runs.Add(new Run(start, end));
            }
        }

        return runs;
    }

    private static void AppendRun(
        Run run,
        int y,
        bool forward,
        RasterGlyph raster,
        int objectIndex,
        List<StitchPoint> destination,
        float maxSegmentLength,
        bool jumpAtStart)
    {
        var startX = forward ? run.Start : run.End;
        var endX = forward ? run.End : run.Start;

        var start = raster.ToWorld(startX, y);
        var end = raster.ToWorld(endX, y);

        destination.Add(new StitchPoint(
            start.X,
            start.Y,
            jumpAtStart ? StitchCommand.Jump : StitchCommand.Stitch,
            objectIndex));

        AppendSegmented(
            start,
            end,
            objectIndex,
            destination,
            maxSegmentLength / raster.Scale);
    }

    private static void AppendPolyline(
        IReadOnlyList<PixelPoint> source,
        RasterGlyph raster,
        int objectIndex,
        List<StitchPoint> destination,
        float maxSegmentLength,
        bool jumpAtStart)
    {
        if (source.Count == 0)
        {
            return;
        }

        var first = raster.ToWorld(source[0].X, source[0].Y);

        destination.Add(new StitchPoint(
            first.X,
            first.Y,
            jumpAtStart ? StitchCommand.Jump : StitchCommand.Stitch,
            objectIndex));

        for (var i = 1; i < source.Count; i++)
        {
            var next = raster.ToWorld(source[i].X, source[i].Y);

            AppendSegmented(
                new SKPoint(
                    destination[^1].X,
                    destination[^1].Y),
                next,
                objectIndex,
                destination,
                maxSegmentLength / raster.Scale);
        }
    }

    private static void AppendSegmented(
        SKPoint from,
        SKPoint to,
        int objectIndex,
        List<StitchPoint> destination,
        float maxSegmentLength)
    {
        var dx = to.X - from.X;
        var dy = to.Y - from.Y;
        var length = MathF.Sqrt(dx * dx + dy * dy);

        var segments = Math.Max(
            1,
            (int)MathF.Ceiling(
                length / MathF.Max(1f, maxSegmentLength)));

        for (var i = 1; i <= segments; i++)
        {
            var ratio = i / (float)segments;

            destination.Add(new StitchPoint(
                from.X + dx * ratio,
                from.Y + dy * ratio,
                StitchCommand.Stitch,
                objectIndex));
        }
    }

    private static PixelPoint? FindNearestInsidePoint(
        Component component,
        float cx,
        float cy,
        float nx,
        float ny)
    {
        var limit = MathF.Max(component.Width, component.Height);

        for (var distance = 0f; distance <= limit; distance += 1f)
        {
            foreach (var sign in new[] { 1f, -1f })
            {
                var x = (int)MathF.Round(cx + nx * distance * sign);
                var y = (int)MathF.Round(cy + ny * distance * sign);

                if (component.Contains(x, y))
                {
                    return new PixelPoint(x, y);
                }
            }
        }

        return null;
    }

    private static PrincipalGeometry ComputePrincipalGeometry(
        Component component)
    {
        var count = component.Pixels.Count;

        var cx = 0f;
        var cy = 0f;

        foreach (var index in component.Pixels)
        {
            cx += index % component.CanvasWidth;
            cy += index / component.CanvasWidth;
        }

        cx /= count;
        cy /= count;

        var xx = 0f;
        var xy = 0f;
        var yy = 0f;

        foreach (var index in component.Pixels)
        {
            var x = index % component.CanvasWidth - cx;
            var y = index / component.CanvasWidth - cy;

            xx += x * x;
            xy += x * y;
            yy += y * y;
        }

        var angle = 0.5f * MathF.Atan2(
            2f * xy,
            xx - yy);

        var axisX = MathF.Cos(angle);
        var axisY = MathF.Sin(angle);
        var normalX = -axisY;
        var normalY = axisX;

        var minT = float.MaxValue;
        var maxT = float.MinValue;

        foreach (var index in component.Pixels)
        {
            var x = index % component.CanvasWidth - cx;
            var y = index / component.CanvasWidth - cy;
            var t = x * axisX + y * axisY;

            minT = MathF.Min(minT, t);
            maxT = MathF.Max(maxT, t);
        }

        return new PrincipalGeometry(
            cx,
            cy,
            axisX,
            axisY,
            normalX,
            normalY,
            minT,
            maxT);
    }

    private static float Distance(
        PixelPoint a,
        PixelPoint b)
    {
        var dx = a.X - b.X;
        var dy = a.Y - b.Y;

        return MathF.Sqrt(dx * dx + dy * dy);
    }

    private sealed record RasterGlyph(
        bool[] Mask,
        int Width,
        int Height,
        float Scale,
        float OriginX,
        float OriginY) : IDisposable
    {
        public SKPoint ToWorld(float x, float y) =>
            new(
                OriginX + x / Scale,
                OriginY + y / Scale);

        public void Dispose()
        {
        }
    }

    private sealed class Component
    {
        public Component(
            List<int> pixels,
            bool[] mask,
            int minX,
            int minY,
            int maxX,
            int maxY,
            int canvasWidth,
            int canvasHeight)
        {
            Pixels = pixels;
            Mask = mask;
            MinX = minX;
            MinY = minY;
            MaxX = maxX;
            MaxY = maxY;
            CanvasWidth = canvasWidth;
            CanvasHeight = canvasHeight;
        }

        public List<int> Pixels { get; }
        public bool[] Mask { get; }
        public int MinX { get; }
        public int MinY { get; }
        public int MaxX { get; }
        public int MaxY { get; }
        public int CanvasWidth { get; }
        public int CanvasHeight { get; }
        public int Width => MaxX - MinX + 1;
        public int Height => MaxY - MinY + 1;

        public bool Contains(int x, int y)
        {
            if (
                x < 0 ||
                y < 0 ||
                x >= CanvasWidth ||
                y >= CanvasHeight)
            {
                return false;
            }

            return Mask[y * CanvasWidth + x];
        }
    }

    private readonly record struct PixelPoint(
        float X,
        float Y);

    private readonly record struct SatinRow(
        PixelPoint A,
        PixelPoint B);

    private readonly record struct Interval(
        float Start,
        float End)
    {
        public float Length => End - Start;
    }

    private readonly record struct Run(
        int Start,
        int End);

    private readonly record struct PrincipalGeometry(
        float Cx,
        float Cy,
        float AxisX,
        float AxisY,
        float NormalX,
        float NormalY,
        float MinT,
        float MaxT);
}

internal static class NumberExtensions
{
    public static float Clamp(
        this float value,
        float min,
        float max) =>
        MathF.Min(max, MathF.Max(min, value));
}
