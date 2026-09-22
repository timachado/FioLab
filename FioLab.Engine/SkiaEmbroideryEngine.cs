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
            // A glyph-sized bounding box is not a reliable embroidery
            // classification. Script letters are often one connected shape
            // made from several narrow branches plus small junctions. Try the
            // medial-axis/topology route first and fall back to the original
            // whole-component classifier only when no stable branches can be
            // extracted.
            var topologyObjects = BuildTopologyObjects(
                component,
                raster,
                options,
                ref nextObjectIndex);

            if (topologyObjects.Count > 0)
            {
                result.AddRange(topologyObjects);
                continue;
            }

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

    private static List<EmbroideryObject> BuildTopologyObjects(
        Component component,
        RasterGlyph raster,
        DigitizeOptions options,
        ref int nextObjectIndex)
    {
        if (component.Pixels.Count < 24)
        {
            return [];
        }

        var skeleton = ThinMask(component);

        if (CountTrue(skeleton) < 4)
        {
            return [];
        }

        var rawJunctionMask = BuildRawJunctionMask(
            component,
            skeleton);

        var rawJunctionCenters = FindMaskRegionCenters(
            rawJunctionMask,
            component.CanvasWidth,
            component.CanvasHeight);

        // Raster thinning creates tiny one/two-pixel spurs on curves.
        // Those pixels used to be interpreted as real 3-way junctions and
        // fragmented one physical stroke into many embroidery objects.
        // A junction is now accepted only when at least three skeleton arms
        // remain visible for a meaningful physical distance.
        var armProbeRadius = MathF.Max(
            6f,
            options.DensityPx * raster.Scale * 1.75f);

        var junctionCenters = rawJunctionCenters
            .Where(center =>
                CountRobustSkeletonArms(
                    skeleton,
                    component,
                    center,
                    armProbeRadius) >= 3)
            .ToList();

        var stableJunctionMask = BuildPointMask(
            junctionCenters,
            component.CanvasWidth,
            component.CanvasHeight);

        var blockRadius = Math.Max(
            2,
            (int)MathF.Round(
                options.DensityPx * raster.Scale * 0.75f));

        var blocked = DilateMask(
            stableJunctionMask,
            component,
            blockRadius);

        var minimumBranchPixels = Math.Max(
            6,
            (int)MathF.Round(
                options.DensityPx * raster.Scale * 1.25f));

        var branchPaths = TraceSkeletonBranches(
                skeleton,
                blocked,
                component)
            .Where(path => path.Count >= minimumBranchPixels)
            .ToList();

        if (branchPaths.Count == 0)
        {
            return [];
        }

        var prepared = new List<TopologyCandidate>();

        foreach (var path in branchPaths)
        {
            var rows = BuildRowsFromSkeletonPath(
                path,
                component,
                raster,
                options);

            if (rows.Count < 2)
            {
                continue;
            }

            var widths = rows
                .Select(row =>
                    Distance(row.A, row.B) / raster.Scale)
                .OrderBy(static width => width)
                .ToArray();

            var medianWidth = widths[widths.Length / 2];

            // If a branch is genuinely much wider than a valid satin
            // column, the topology route is not appropriate for this
            // component. Fall back to the whole-object Tatami classifier
            // instead of emitting partial coverage.
            if (medianWidth > options.MaxSatinWidthPx * 1.60f)
            {
                return [];
            }

            var centers = rows
                .Select(static row => new PixelPoint(
                    (row.A.X + row.B.X) / 2f,
                    (row.A.Y + row.B.Y) / 2f))
                .ToList();

            var kind =
                medianWidth <= options.RunningMaxWidthPx * 1.15f
                    ? EmbroideryObjectKind.Running
                    : EmbroideryObjectKind.Satin;

            prepared.Add(new TopologyCandidate(
                kind,
                rows,
                centers));
        }

        if (prepared.Count == 0)
        {
            return [];
        }

        var mergeRadius = MathF.Max(
            7f,
            options.DensityPx * raster.Scale * 2.15f);

        // Tiny candidates touching a real junction are normally raster
        // spurs, not independent physical strokes. Drop them before routing.
        prepared = prepared
            .Where(candidate =>
                !IsMicroBranchNearJunction(
                    candidate,
                    junctionCenters,
                    mergeRadius))
            .ToList();

        if (prepared.Count == 0)
        {
            return [];
        }

        // At a 3-way meeting, continue the two most collinear branches as
        // one physical embroidery object. The side branch remains separate.
        // This mirrors how a digitizer follows the main stroke through a
        // junction instead of creating a new object for every graph edge.
        prepared = MergeThroughJunctions(
            prepared,
            junctionCenters,
            mergeRadius);

        // Stable, deterministic order inside a glyph. Each branch is fully
        // completed before the next one begins.
        prepared = prepared
            .OrderBy(static candidate =>
                candidate.Centers.Min(static point => point.X))
            .ThenBy(static candidate =>
                candidate.Centers.Min(static point => point.Y))
            .ToList();

        var result = new List<EmbroideryObject>();

        foreach (var candidate in prepared)
        {
            var objectIndex = nextObjectIndex++;

            var points =
                candidate.Kind == EmbroideryObjectKind.Running
                    ? BuildRunningFromCenterline(
                        candidate.Centers,
                        component,
                        raster,
                        objectIndex,
                        options)
                    : BuildSatinFromRows(
                        candidate.Rows,
                        component,
                        raster,
                        objectIndex,
                        options);

            if (points.Count < 2)
            {
                continue;
            }

            result.Add(new EmbroideryObject
            {
                Index = objectIndex,
                Kind = candidate.Kind,
                Points = points
            });
        }

        // Narrow script junctions are intentionally not emitted as separate
        // Tatami objects. The main Satin continuation covers the meeting
        // area and avoids the small boxes/triangles seen in the preview.

        return result;
    }

    private static bool[] ThinMask(
        Component component)
    {
        var skeleton = new bool[component.Mask.Length];
        Array.Copy(
            component.Mask,
            skeleton,
            component.Mask.Length);

        var width = component.CanvasWidth;
        var height = component.CanvasHeight;
        var changed = true;
        var iterations = 0;
        var toDelete = new List<int>();

        while (changed && iterations < 256)
        {
            changed = false;
            iterations++;

            for (var phase = 0; phase < 2; phase++)
            {
                toDelete.Clear();

                for (var y = Math.Max(1, component.MinY);
                     y <= Math.Min(height - 2, component.MaxY);
                     y++)
                {
                    for (var x = Math.Max(1, component.MinX);
                         x <= Math.Min(width - 2, component.MaxX);
                         x++)
                    {
                        var index = y * width + x;

                        if (!skeleton[index])
                        {
                            continue;
                        }

                        var p2 = skeleton[(y - 1) * width + x];
                        var p3 = skeleton[(y - 1) * width + x + 1];
                        var p4 = skeleton[y * width + x + 1];
                        var p5 = skeleton[(y + 1) * width + x + 1];
                        var p6 = skeleton[(y + 1) * width + x];
                        var p7 = skeleton[(y + 1) * width + x - 1];
                        var p8 = skeleton[y * width + x - 1];
                        var p9 = skeleton[(y - 1) * width + x - 1];

                        var neighbors =
                            BoolInt(p2) + BoolInt(p3) +
                            BoolInt(p4) + BoolInt(p5) +
                            BoolInt(p6) + BoolInt(p7) +
                            BoolInt(p8) + BoolInt(p9);

                        if (neighbors < 2 || neighbors > 6)
                        {
                            continue;
                        }

                        var transitions = CountTransitions(
                            p2, p3, p4, p5,
                            p6, p7, p8, p9);

                        if (transitions != 1)
                        {
                            continue;
                        }

                        var keep =
                            phase == 0
                                ? p2 && p4 && p6 ||
                                  p4 && p6 && p8
                                : p2 && p4 && p8 ||
                                  p2 && p6 && p8;

                        if (!keep)
                        {
                            toDelete.Add(index);
                        }
                    }
                }

                if (toDelete.Count == 0)
                {
                    continue;
                }

                changed = true;

                foreach (var index in toDelete)
                {
                    skeleton[index] = false;
                }
            }
        }

        return skeleton;
    }

    private static int BoolInt(bool value) =>
        value ? 1 : 0;

    private static int CountTransitions(
        params bool[] neighbors)
    {
        var transitions = 0;

        for (var i = 0; i < neighbors.Length; i++)
        {
            var current = neighbors[i];
            var next = neighbors[(i + 1) % neighbors.Length];

            if (!current && next)
            {
                transitions++;
            }
        }

        return transitions;
    }

    private static int CountTrue(
        bool[] mask)
    {
        var count = 0;

        foreach (var value in mask)
        {
            if (value)
            {
                count++;
            }
        }

        return count;
    }

    private static bool[] BuildRawJunctionMask(
        Component component,
        bool[] skeleton)
    {
        var result = new bool[skeleton.Length];
        var width = component.CanvasWidth;

        for (var y = Math.Max(1, component.MinY);
             y <= Math.Min(component.CanvasHeight - 2, component.MaxY);
             y++)
        {
            for (var x = Math.Max(1, component.MinX);
                 x <= Math.Min(width - 2, component.MaxX);
                 x++)
            {
                var index = y * width + x;

                if (!skeleton[index])
                {
                    continue;
                }

                var neighbors = GetNeighborFlags(
                    skeleton,
                    width,
                    x,
                    y);

                // Counting connected neighbor groups instead of raw neighbor
                // count avoids treating a diagonal stair-step as a branch.
                if (CountTransitions(neighbors) >= 3)
                {
                    result[index] = true;
                }
            }
        }

        return result;
    }

    private static bool[] GetNeighborFlags(
        bool[] mask,
        int width,
        int x,
        int y) =>
    [
        mask[(y - 1) * width + x],
        mask[(y - 1) * width + x + 1],
        mask[y * width + x + 1],
        mask[(y + 1) * width + x + 1],
        mask[(y + 1) * width + x],
        mask[(y + 1) * width + x - 1],
        mask[y * width + x - 1],
        mask[(y - 1) * width + x - 1]
    ];

    private static List<PixelPoint> FindMaskRegionCenters(
        bool[] mask,
        int width,
        int height)
    {
        var visited = new bool[mask.Length];
        var result = new List<PixelPoint>();

        for (var seed = 0; seed < mask.Length; seed++)
        {
            if (!mask[seed] || visited[seed])
            {
                continue;
            }

            var queue = new Queue<int>();
            queue.Enqueue(seed);
            visited[seed] = true;

            var sumX = 0f;
            var sumY = 0f;
            var count = 0;

            while (queue.Count > 0)
            {
                var index = queue.Dequeue();
                var x = index % width;
                var y = index / width;

                sumX += x;
                sumY += y;
                count++;

                foreach (var next in NeighborIndices8(
                    x,
                    y,
                    width,
                    height))
                {
                    if (!mask[next] || visited[next])
                    {
                        continue;
                    }

                    visited[next] = true;
                    queue.Enqueue(next);
                }
            }

            if (count > 0)
            {
                result.Add(new PixelPoint(
                    sumX / count,
                    sumY / count));
            }
        }

        return result;
    }

    private static bool[] BuildPointMask(
        IReadOnlyList<PixelPoint> centers,
        int width,
        int height)
    {
        var result = new bool[width * height];

        foreach (var center in centers)
        {
            var x = Math.Clamp(
                (int)MathF.Round(center.X),
                0,
                width - 1);

            var y = Math.Clamp(
                (int)MathF.Round(center.Y),
                0,
                height - 1);

            result[y * width + x] = true;
        }

        return result;
    }

    private static int CountRobustSkeletonArms(
        bool[] skeleton,
        Component component,
        PixelPoint center,
        float outerRadius)
    {
        var width = component.CanvasWidth;
        var height = component.CanvasHeight;
        var innerRadius = MathF.Max(
            1.75f,
            outerRadius * 0.28f);

        var innerSquared = innerRadius * innerRadius;
        var outerSquared = outerRadius * outerRadius;

        var local = new bool[skeleton.Length];

        var minX = Math.Max(
            0,
            (int)MathF.Floor(center.X - outerRadius - 1f));

        var maxX = Math.Min(
            width - 1,
            (int)MathF.Ceiling(center.X + outerRadius + 1f));

        var minY = Math.Max(
            0,
            (int)MathF.Floor(center.Y - outerRadius - 1f));

        var maxY = Math.Min(
            height - 1,
            (int)MathF.Ceiling(center.Y + outerRadius + 1f));

        for (var y = minY; y <= maxY; y++)
        {
            for (var x = minX; x <= maxX; x++)
            {
                var index = y * width + x;

                if (!skeleton[index])
                {
                    continue;
                }

                var dx = x - center.X;
                var dy = y - center.Y;
                var distanceSquared = dx * dx + dy * dy;

                if (
                    distanceSquared <= innerSquared ||
                    distanceSquared > outerSquared)
                {
                    continue;
                }

                local[index] = true;
            }
        }

        var visited = new bool[skeleton.Length];
        var robustArms = 0;

        for (var seed = 0; seed < local.Length; seed++)
        {
            if (!local[seed] || visited[seed])
            {
                continue;
            }

            var queue = new Queue<int>();
            queue.Enqueue(seed);
            visited[seed] = true;

            var touchesInner = false;
            var touchesOuter = false;

            while (queue.Count > 0)
            {
                var current = queue.Dequeue();
                var x = current % width;
                var y = current / width;

                var dx = x - center.X;
                var dy = y - center.Y;
                var distance = MathF.Sqrt(dx * dx + dy * dy);

                if (distance <= innerRadius + 1.5f)
                {
                    touchesInner = true;
                }

                if (distance >= outerRadius - 1.5f)
                {
                    touchesOuter = true;
                }

                foreach (var next in NeighborIndices8(
                    x,
                    y,
                    width,
                    height))
                {
                    if (!local[next] || visited[next])
                    {
                        continue;
                    }

                    visited[next] = true;
                    queue.Enqueue(next);
                }
            }

            if (touchesInner && touchesOuter)
            {
                robustArms++;
            }
        }

        return robustArms;
    }

    private static bool[] DilateMask(
        bool[] source,
        Component component,
        int radius)
    {
        var result = new bool[source.Length];
        var width = component.CanvasWidth;
        var height = component.CanvasHeight;
        var radiusSquared = radius * radius;

        for (var y = component.MinY; y <= component.MaxY; y++)
        {
            for (var x = component.MinX; x <= component.MaxX; x++)
            {
                if (!source[y * width + x])
                {
                    continue;
                }

                for (var oy = -radius; oy <= radius; oy++)
                {
                    var ny = y + oy;

                    if (ny < 0 || ny >= height)
                    {
                        continue;
                    }

                    for (var ox = -radius; ox <= radius; ox++)
                    {
                        if (ox * ox + oy * oy > radiusSquared)
                        {
                            continue;
                        }

                        var nx = x + ox;

                        if (nx < 0 || nx >= width)
                        {
                            continue;
                        }

                        result[ny * width + nx] = true;
                    }
                }
            }
        }

        return result;
    }

    private static List<List<int>> TraceSkeletonBranches(
        bool[] skeleton,
        bool[] blocked,
        Component component)
    {
        var available = new bool[skeleton.Length];

        foreach (var index in component.Pixels)
        {
            available[index] =
                skeleton[index] &&
                !blocked[index];
        }

        var width = component.CanvasWidth;
        var height = component.CanvasHeight;
        var visited = new bool[available.Length];
        var paths = new List<List<int>>();

        for (var seed = 0; seed < available.Length; seed++)
        {
            if (!available[seed] || visited[seed])
            {
                continue;
            }

            var queue = new Queue<int>();
            var region = new List<int>();
            queue.Enqueue(seed);
            visited[seed] = true;

            while (queue.Count > 0)
            {
                var index = queue.Dequeue();
                region.Add(index);

                var x = index % width;
                var y = index / width;

                foreach (var next in NeighborIndices8(
                    x,
                    y,
                    width,
                    height))
                {
                    if (!available[next] || visited[next])
                    {
                        continue;
                    }

                    visited[next] = true;
                    queue.Enqueue(next);
                }
            }

            paths.AddRange(OrderSkeletonRegion(
                region,
                width,
                height));
        }

        return paths
            .Where(static path => path.Count >= 3)
            .ToList();
    }

    private static List<List<int>> OrderSkeletonRegion(
        List<int> region,
        int width,
        int height)
    {
        var remaining = new HashSet<int>(region);
        var result = new List<List<int>>();

        while (remaining.Count > 0)
        {
            var start = remaining
                .OrderBy(index =>
                    CountNeighborsInSet(
                        index,
                        remaining,
                        width,
                        height))
                .ThenBy(static index => index)
                .First();

            var path = new List<int>();
            int? previous = null;
            var current = start;

            while (true)
            {
                path.Add(current);
                remaining.Remove(current);

                var x = current % width;
                var y = current / width;

                var candidates = NeighborIndices8(
                        x,
                        y,
                        width,
                        height)
                    .Where(remaining.Contains)
                    .ToList();

                if (candidates.Count == 0)
                {
                    break;
                }

                int next;

                if (previous is null || candidates.Count == 1)
                {
                    next = candidates[0];
                }
                else
                {
                    var px = previous.Value % width;
                    var py = previous.Value / width;
                    var vx = x - px;
                    var vy = y - py;
                    var vLength = MathF.Max(
                        0.001f,
                        MathF.Sqrt(vx * vx + vy * vy));

                    next = candidates
                        .OrderByDescending(candidate =>
                        {
                            var cx = candidate % width - x;
                            var cy = candidate / width - y;
                            var cLength = MathF.Max(
                                0.001f,
                                MathF.Sqrt(cx * cx + cy * cy));

                            return
                                (vx * cx + vy * cy) /
                                (vLength * cLength);
                        })
                        .First();
                }

                previous = current;
                current = next;
            }

            if (path.Count >= 3)
            {
                result.Add(path);
            }
        }

        return result;
    }

    private static int CountNeighborsInSet(
        int index,
        HashSet<int> set,
        int width,
        int height)
    {
        var x = index % width;
        var y = index / width;
        var count = 0;

        foreach (var next in NeighborIndices8(
            x,
            y,
            width,
            height))
        {
            if (set.Contains(next))
            {
                count++;
            }
        }

        return count;
    }

    private static IEnumerable<int> NeighborIndices8(
        int x,
        int y,
        int width,
        int height)
    {
        for (var oy = -1; oy <= 1; oy++)
        {
            for (var ox = -1; ox <= 1; ox++)
            {
                if (ox == 0 && oy == 0)
                {
                    continue;
                }

                var nx = x + ox;
                var ny = y + oy;

                if (
                    nx < 0 ||
                    ny < 0 ||
                    nx >= width ||
                    ny >= height)
                {
                    continue;
                }

                yield return ny * width + nx;
            }
        }
    }

    private static List<SatinRow> BuildRowsFromSkeletonPath(
        IReadOnlyList<int> path,
        Component component,
        RasterGlyph raster,
        DigitizeOptions options)
    {
        var width = component.CanvasWidth;
        var raw = path
            .Select(index => new PixelPoint(
                index % width,
                index / width))
            .ToList();

        var pitch = MathF.Max(
            2f,
            options.DensityPx * raster.Scale);

        var centers = ResampleCenterline(
            raw,
            pitch);

        if (centers.Count < 2)
        {
            return [];
        }

        var rows = new List<SatinRow>();
        SatinRow? previousRow = null;
        var maxRay = MathF.Sqrt(
            component.Width * component.Width +
            component.Height * component.Height) + 4f;

        for (var i = 0; i < centers.Count; i++)
        {
            var before = centers[Math.Max(0, i - 1)];
            var after = centers[Math.Min(centers.Count - 1, i + 1)];

            var tx = after.X - before.X;
            var ty = after.Y - before.Y;
            var length = MathF.Sqrt(tx * tx + ty * ty);

            if (length < 0.001f)
            {
                continue;
            }

            tx /= length;
            ty /= length;

            var nx = -ty;
            var ny = tx;

            var positive = RayDistanceInside(
                component,
                centers[i],
                nx,
                ny,
                maxRay);

            var negative = RayDistanceInside(
                component,
                centers[i],
                -nx,
                -ny,
                maxRay);

            if (positive < 0.75f || negative < 0.75f)
            {
                continue;
            }

            var candidate = new SatinRow(
                new PixelPoint(
                    centers[i].X - nx * negative,
                    centers[i].Y - ny * negative),
                new PixelPoint(
                    centers[i].X + nx * positive,
                    centers[i].Y + ny * positive));

            if (!SegmentInsideComponent(
                component,
                candidate.A,
                candidate.B))
            {
                continue;
            }

            if (previousRow is not null)
            {
                var direct =
                    Distance(previousRow.Value.A, candidate.A) +
                    Distance(previousRow.Value.B, candidate.B);

                var swapped =
                    Distance(previousRow.Value.A, candidate.B) +
                    Distance(previousRow.Value.B, candidate.A);

                if (swapped < direct)
                {
                    candidate = new SatinRow(
                        candidate.B,
                        candidate.A);
                }
            }

            rows.Add(candidate);
            previousRow = candidate;
        }

        return rows;
    }

    private static List<PixelPoint> ResampleCenterline(
        IReadOnlyList<PixelPoint> source,
        float pitch)
    {
        if (source.Count < 2)
        {
            return source.ToList();
        }

        var result = new List<PixelPoint>
        {
            source[0]
        };

        var last = source[0];

        for (var i = 1; i < source.Count; i++)
        {
            var point = source[i];

            if (Distance(last, point) < pitch)
            {
                continue;
            }

            result.Add(point);
            last = point;
        }

        if (
            Distance(result[^1], source[^1]) >
            pitch * 0.35f)
        {
            result.Add(source[^1]);
        }

        return result;
    }

    private static float RayDistanceInside(
        Component component,
        PixelPoint center,
        float dx,
        float dy,
        float maxDistance)
    {
        var lastInside = 0f;

        for (var distance = 0.5f;
             distance <= maxDistance;
             distance += 0.5f)
        {
            var x = (int)MathF.Round(
                center.X + dx * distance);

            var y = (int)MathF.Round(
                center.Y + dy * distance);

            if (!component.Contains(x, y))
            {
                break;
            }

            lastInside = distance;
        }

        return lastInside;
    }

    private static bool SegmentInsideComponent(
        Component component,
        PixelPoint a,
        PixelPoint b)
    {
        for (var sample = 1; sample < 10; sample++)
        {
            var ratio = sample / 10f;

            var x = (int)MathF.Round(
                a.X + (b.X - a.X) * ratio);

            var y = (int)MathF.Round(
                a.Y + (b.Y - a.Y) * ratio);

            if (!component.Contains(x, y))
            {
                return false;
            }
        }

        return true;
    }

    private static List<StitchPoint> BuildRunningFromCenterline(
        IReadOnlyList<PixelPoint> centers,
        Component component,
        RasterGlyph raster,
        int objectIndex,
        DigitizeOptions options)
    {
        var points = new List<StitchPoint>();

        AppendSafePolyline(
            centers,
            component,
            raster,
            objectIndex,
            points,
            options.StitchLengthPx * raster.Scale);

        return points;
    }

    private static List<StitchPoint> BuildSatinFromRows(
        IReadOnlyList<SatinRow> rows,
        Component component,
        RasterGlyph raster,
        int objectIndex,
        DigitizeOptions options)
    {
        var points = new List<StitchPoint>();

        if (rows.Count < 2)
        {
            return points;
        }

        if (options.IncludeUnderlay)
        {
            var centers = rows
                .Select(static row => new PixelPoint(
                    (row.A.X + row.B.X) / 2f,
                    (row.A.Y + row.B.Y) / 2f))
                .ToList();

            AppendSafePolyline(
                centers,
                component,
                raster,
                objectIndex,
                points,
                options.StitchLengthPx * raster.Scale);
        }

        PixelPoint? previousEnd = null;

        for (var i = 0; i < rows.Count; i++)
        {
            var row = rows[i];

            var start =
                i % 2 == 0
                    ? row.A
                    : row.B;

            var end =
                i % 2 == 0
                    ? row.B
                    : row.A;

            var startWorld = raster.ToWorld(
                start.X,
                start.Y);

            var endWorld = raster.ToWorld(
                end.X,
                end.Y);

            var startCommand =
                previousEnd is not null &&
                SegmentInsideComponent(
                    component,
                    previousEnd.Value,
                    start)
                    ? StitchCommand.Stitch
                    : StitchCommand.Jump;

            points.Add(new StitchPoint(
                startWorld.X,
                startWorld.Y,
                startCommand,
                objectIndex));

            points.Add(new StitchPoint(
                endWorld.X,
                endWorld.Y,
                StitchCommand.Stitch,
                objectIndex));

            previousEnd = end;
        }

        return points;
    }

    private static void AppendSafePolyline(
        IReadOnlyList<PixelPoint> source,
        Component component,
        RasterGlyph raster,
        int objectIndex,
        List<StitchPoint> destination,
        float maxSegmentLength)
    {
        if (source.Count == 0)
        {
            return;
        }

        var first = raster.ToWorld(
            source[0].X,
            source[0].Y);

        destination.Add(new StitchPoint(
            first.X,
            first.Y,
            StitchCommand.Jump,
            objectIndex));

        for (var i = 1; i < source.Count; i++)
        {
            var current = source[i];
            var previous = source[i - 1];
            var next = raster.ToWorld(
                current.X,
                current.Y);

            if (!SegmentInsideComponent(
                component,
                previous,
                current))
            {
                destination.Add(new StitchPoint(
                    next.X,
                    next.Y,
                    StitchCommand.Jump,
                    objectIndex));

                continue;
            }

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

    private static bool IsMicroBranchNearJunction(
        TopologyCandidate candidate,
        IReadOnlyList<PixelPoint> junctions,
        float radius)
    {
        if (candidate.Centers.Count > 3 || junctions.Count == 0)
        {
            return false;
        }

        var start = candidate.Centers[0];
        var end = candidate.Centers[^1];

        return junctions.Any(junction =>
            Distance(start, junction) <= radius * 1.15f ||
            Distance(end, junction) <= radius * 1.15f);
    }

    private static List<TopologyCandidate> MergeThroughJunctions(
        List<TopologyCandidate> source,
        IReadOnlyList<PixelPoint> junctions,
        float radius)
    {
        var candidates = source.ToList();

        foreach (var junction in junctions)
        {
            var endpoints = new List<CandidateEndpoint>();

            for (var index = 0; index < candidates.Count; index++)
            {
                var candidate = candidates[index];

                if (candidate.Centers.Count < 2)
                {
                    continue;
                }

                AddEndpoint(index, atStart: true);
                AddEndpoint(index, atStart: false);

                continue;

                void AddEndpoint(
                    int candidateIndex,
                    bool atStart)
                {
                    var current = candidates[candidateIndex];
                    var endpoint = atStart
                        ? current.Centers[0]
                        : current.Centers[^1];

                    if (Distance(endpoint, junction) > radius)
                    {
                        return;
                    }

                    var adjacent = atStart
                        ? current.Centers[1]
                        : current.Centers[^2];

                    var direction = NormalizeVector(
                        adjacent.X - endpoint.X,
                        adjacent.Y - endpoint.Y);

                    endpoints.Add(new CandidateEndpoint(
                        candidateIndex,
                        atStart,
                        endpoint,
                        direction.X,
                        direction.Y));
                }
            }

            CandidatePair? best = null;

            for (var a = 0; a < endpoints.Count; a++)
            {
                for (var b = a + 1; b < endpoints.Count; b++)
                {
                    var first = endpoints[a];
                    var second = endpoints[b];

                    if (first.CandidateIndex == second.CandidateIndex)
                    {
                        continue;
                    }

                    var pairCandidateA = candidates[first.CandidateIndex];
                    var pairCandidateB = candidates[second.CandidateIndex];

                    if (pairCandidateA.Kind != pairCandidateB.Kind)
                    {
                        continue;
                    }

                    var dot =
                        first.DirectionX * second.DirectionX +
                        first.DirectionY * second.DirectionY;

                    // Opposite outward tangents mean one continuous stroke
                    // passing through the junction.
                    if (dot > -0.55f)
                    {
                        continue;
                    }

                    var score =
                        dot +
                        0.025f *
                        (
                            Distance(first.Point, junction) +
                            Distance(second.Point, junction)
                        );

                    if (best is null || score < best.Value.Score)
                    {
                        best = new CandidatePair(
                            first,
                            second,
                            score);
                    }
                }
            }

            if (best is null)
            {
                continue;
            }

            var pair = best.Value;
            var firstIndex = pair.First.CandidateIndex;
            var secondIndex = pair.Second.CandidateIndex;

            var firstCandidate = OrientCandidateToJunctionEnd(
                candidates[firstIndex],
                pair.First.AtStart);

            var secondCandidate = OrientCandidateFromJunctionStart(
                candidates[secondIndex],
                pair.Second.AtStart);

            var mergedCenters = new List<PixelPoint>(
                firstCandidate.Centers.Count +
                secondCandidate.Centers.Count + 1);

            mergedCenters.AddRange(firstCandidate.Centers);

            if (
                Distance(mergedCenters[^1], junction) >
                    0.75f &&
                Distance(junction, secondCandidate.Centers[0]) >
                    0.75f)
            {
                mergedCenters.Add(junction);
            }

            mergedCenters.AddRange(secondCandidate.Centers);

            var mergedRows = new List<SatinRow>(
                firstCandidate.Rows.Count +
                secondCandidate.Rows.Count);

            mergedRows.AddRange(firstCandidate.Rows);
            mergedRows.AddRange(secondCandidate.Rows);
            mergedRows = AlignSatinRows(mergedRows);

            var merged = new TopologyCandidate(
                firstCandidate.Kind,
                mergedRows,
                mergedCenters);

            var high = Math.Max(firstIndex, secondIndex);
            var low = Math.Min(firstIndex, secondIndex);

            candidates.RemoveAt(high);
            candidates.RemoveAt(low);
            candidates.Add(merged);
        }

        return candidates;
    }

    private static TopologyCandidate OrientCandidateToJunctionEnd(
        TopologyCandidate candidate,
        bool junctionAtStart) =>
        junctionAtStart
            ? ReverseTopologyCandidate(candidate)
            : candidate;

    private static TopologyCandidate OrientCandidateFromJunctionStart(
        TopologyCandidate candidate,
        bool junctionAtStart) =>
        junctionAtStart
            ? candidate
            : ReverseTopologyCandidate(candidate);

    private static TopologyCandidate ReverseTopologyCandidate(
        TopologyCandidate candidate)
    {
        var centers = candidate.Centers
            .AsEnumerable()
            .Reverse()
            .ToList();

        var rows = candidate.Rows
            .AsEnumerable()
            .Reverse()
            .ToList();

        return new TopologyCandidate(
            candidate.Kind,
            AlignSatinRows(rows),
            centers);
    }

    private static List<SatinRow> AlignSatinRows(
        IReadOnlyList<SatinRow> source)
    {
        var result = new List<SatinRow>(source.Count);
        SatinRow? previous = null;

        foreach (var raw in source)
        {
            var current = raw;

            if (previous is not null)
            {
                var direct =
                    Distance(previous.Value.A, current.A) +
                    Distance(previous.Value.B, current.B);

                var swapped =
                    Distance(previous.Value.A, current.B) +
                    Distance(previous.Value.B, current.A);

                if (swapped < direct)
                {
                    current = new SatinRow(
                        current.B,
                        current.A);
                }
            }

            result.Add(current);
            previous = current;
        }

        return result;
    }

    private static (float X, float Y) NormalizeVector(
        float x,
        float y)
    {
        var length = MathF.Sqrt(x * x + y * y);

        if (length < 0.0001f)
        {
            return (0f, 0f);
        }

        return (x / length, y / length);
    }

    private readonly record struct CandidateEndpoint(
        int CandidateIndex,
        bool AtStart,
        PixelPoint Point,
        float DirectionX,
        float DirectionY);

    private readonly record struct CandidatePair(
        CandidateEndpoint First,
        CandidateEndpoint Second,
        float Score);

    private static bool[] BuildJunctionPatchMask(
        Component component,
        IReadOnlyList<PixelPoint> centers,
        RasterGlyph raster,
        DigitizeOptions options)
    {
        var result = new bool[component.Mask.Length];

        var radius = MathF.Max(
            3f,
            options.DensityPx * raster.Scale * 2.2f);

        var radiusSquared = radius * radius;
        var width = component.CanvasWidth;

        foreach (var index in component.Pixels)
        {
            var x = index % width;
            var y = index / width;

            foreach (var center in centers)
            {
                var dx = x - center.X;
                var dy = y - center.Y;

                if (dx * dx + dy * dy > radiusSquared)
                {
                    continue;
                }

                result[index] = true;
                break;
            }
        }

        return result;
    }

    private readonly record struct TopologyCandidate(
        EmbroideryObjectKind Kind,
        List<SatinRow> Rows,
        List<PixelPoint> Centers);

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

            // A true satin column must have one continuous rail pair per
            // section. Branched/holed sections would force the path to jump
            // from one branch to another before finishing the current one.
            // In that case, abort satin generation and let BuildSatin fall
            // back to Tatami, which covers the complete object.
            if (intervals.Count > 1)
            {
                return [];
            }

            var selected = intervals[0];

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
            var firstUnderlay = true;

            for (var y = component.MinY; y <= component.MaxY; y += underlayStep)
            {
                var runs = FindRuns(component, y);

                if (runs.Count == 0)
                {
                    continue;
                }

                if (!underlayForward)
                {
                    runs.Reverse();
                }

                for (var runIndex = 0; runIndex < runs.Count; runIndex++)
                {
                    AppendRun(
                        runs[runIndex],
                        y,
                        underlayForward,
                        raster,
                        objectIndex,
                        points,
                        options.StitchLengthPx * raster.Scale * 1.5f,
                        jumpAtStart:
                            firstUnderlay ||
                            runIndex > 0);

                    firstUnderlay = false;
                }

                // Reverse only after the whole scan row is complete.
                underlayForward = !underlayForward;
            }
        }

        var forward = true;
        var firstFill = true;

        for (var y = component.MinY; y <= component.MaxY; y += rowStep)
        {
            var runs = FindRuns(component, y);

            if (runs.Count == 0)
            {
                continue;
            }

            // The scanline is one pass. When travelling right-to-left,
            // visit the rightmost span first so the needle reaches the
            // physical end of the row before reversing.
            if (!forward)
            {
                runs.Reverse();
            }

            for (var runIndex = 0; runIndex < runs.Count; runIndex++)
            {
                // Separate spans on the same row can be divided by a hole.
                // Move between those spans with needle-up (Jump), never
                // stitch across empty space. The first span of the next row
                // remains connected to the previous edge, producing the
                // expected edge-to-edge serpentine motion.
                AppendRun(
                    runs[runIndex],
                    y,
                    forward,
                    raster,
                    objectIndex,
                    points,
                    options.StitchLengthPx * raster.Scale,
                    jumpAtStart:
                        firstFill ||
                        runIndex > 0);

                firstFill = false;
            }

            // Critical rule: direction changes once per completed row,
            // never once per interval/run.
            forward = !forward;
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
