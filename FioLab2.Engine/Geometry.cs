using SkiaSharp;

namespace FioLab2.Engine;

internal static class Geometry
{
    private static readonly (int X, int Y)[] NeighborOffsets =
    [
        (-1, -1),
        ( 0, -1),
        ( 1, -1),
        (-1,  0),
        ( 1,  0),
        (-1,  1),
        ( 0,  1),
        ( 1,  1)
    ];

    public static RasterGlyph Rasterize(
        SKPath path,
        DigitizeOptions options)
    {
        var bounds = path.Bounds;
        var scale = Math.Clamp(
            options.RasterScale,
            1.5f,
            4f);

        const int margin = 8;

        var width = Math.Max(
            16,
            (int)MathF.Ceiling(
                bounds.Width * scale) +
            margin * 2);

        var height = Math.Max(
            16,
            (int)MathF.Ceiling(
                bounds.Height * scale) +
            margin * 2);

        using var bitmap = new SKBitmap(
            width,
            height,
            SKColorType.Rgba8888,
            SKAlphaType.Premul);

        using var canvas = new SKCanvas(bitmap);
        using var paint = new SKPaint
        {
            Color = SKColors.White,
            Style = SKPaintStyle.Fill,
            IsAntialias = false
        };

        canvas.Clear(SKColors.Transparent);
        canvas.Scale(scale, scale);
        canvas.Translate(
            -bounds.Left + margin / scale,
            -bounds.Top + margin / scale);
        canvas.DrawPath(path, paint);
        canvas.Flush();

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
            bounds,
            margin);
    }

    public static List<Component> FindComponents(
        RasterGlyph raster)
    {
        var visited = new bool[raster.Mask.Length];
        var result = new List<Component>();
        var queue = new Queue<int>();

        for (var seed = 0;
             seed < raster.Mask.Length;
             seed++)
        {
            if (
                visited[seed] ||
                !raster.Mask[seed])
            {
                continue;
            }

            var pixels = new List<int>();
            var minX = raster.Width;
            var minY = raster.Height;
            var maxX = 0;
            var maxY = 0;

            visited[seed] = true;
            queue.Enqueue(seed);

            while (queue.Count > 0)
            {
                var index = queue.Dequeue();
                pixels.Add(index);

                var x = index % raster.Width;
                var y = index / raster.Width;

                minX = Math.Min(minX, x);
                minY = Math.Min(minY, y);
                maxX = Math.Max(maxX, x);
                maxY = Math.Max(maxY, y);

                foreach (var neighbor in Neighbors(
                    index,
                    raster.Width,
                    raster.Height))
                {
                    if (
                        visited[neighbor] ||
                        !raster.Mask[neighbor])
                    {
                        continue;
                    }

                    visited[neighbor] = true;
                    queue.Enqueue(neighbor);
                }
            }

            if (pixels.Count < 4)
            {
                continue;
            }

            var membership =
                new bool[raster.Mask.Length];

            foreach (var pixel in pixels)
            {
                membership[pixel] = true;
            }

            result.Add(new Component
            {
                Membership = membership,
                CanvasWidth = raster.Width,
                CanvasHeight = raster.Height,
                MinX = minX,
                MinY = minY,
                MaxX = maxX,
                MaxY = maxY,
                PixelCount = pixels.Count
            });
        }

        return result
            .OrderBy(static component =>
                component.MinX)
            .ThenBy(static component =>
                component.MinY)
            .ToList();
    }

    public static IEnumerable<int> Neighbors(
        int index,
        int width,
        int height)
    {
        var x = index % width;
        var y = index / width;

        foreach (var offset in NeighborOffsets)
        {
            var nx = x + offset.X;
            var ny = y + offset.Y;

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

    public static PixelPoint IndexToPoint(
        int index,
        int width) =>
        new(
            index % width,
            index / width);

    public static float Distance(
        PixelPoint a,
        PixelPoint b)
    {
        var dx = b.X - a.X;
        var dy = b.Y - a.Y;
        return MathF.Sqrt(dx * dx + dy * dy);
    }

    public static float PolylineLength(
        IReadOnlyList<PixelPoint> points)
    {
        var length = 0f;

        for (var index = 1;
             index < points.Count;
             index++)
        {
            length += Distance(
                points[index - 1],
                points[index]);
        }

        return length;
    }

    public static PixelPoint Normalize(
        float x,
        float y)
    {
        var length =
            MathF.Sqrt(x * x + y * y);

        if (length < 0.0001f)
        {
            return new PixelPoint(1f, 0f);
        }

        return new PixelPoint(
            x / length,
            y / length);
    }

    public static bool SegmentInside(
        Component component,
        PixelPoint a,
        PixelPoint b)
    {
        for (var sample = 1;
             sample < 16;
             sample++)
        {
            var ratio = sample / 16f;
            var point = new PixelPoint(
                a.X + (b.X - a.X) * ratio,
                a.Y + (b.Y - a.Y) * ratio);

            if (!component.Contains(point))
            {
                return false;
            }
        }

        return true;
    }

    public static bool ProperlyIntersects(
        PixelPoint a,
        PixelPoint b,
        PixelPoint c,
        PixelPoint d)
    {
        var ab1 = Cross(a, b, c);
        var ab2 = Cross(a, b, d);
        var cd1 = Cross(c, d, a);
        var cd2 = Cross(c, d, b);

        const float epsilon = 0.001f;

        return
            ab1 * ab2 < -epsilon &&
            cd1 * cd2 < -epsilon;
    }

    public static float Cross(
        PixelPoint a,
        PixelPoint b,
        PixelPoint c) =>
        (b.X - a.X) * (c.Y - a.Y) -
        (b.Y - a.Y) * (c.X - a.X);

    public static ulong LinkKey(
        int first,
        int second)
    {
        var low = Math.Min(first, second);
        var high = Math.Max(first, second);

        return
            ((ulong)(uint)low << 32) |
            (uint)high;
    }
}
