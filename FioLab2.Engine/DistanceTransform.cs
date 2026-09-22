namespace FioLab2.Engine;

internal static class DistanceTransform
{
    private const float Diagonal =
        1.41421356f;

    public static float[] Compute(
        Component component)
    {
        var width = component.CanvasWidth;
        var height = component.CanvasHeight;
        var distance =
            new float[component.Membership.Length];

        const float infinity = 1_000_000f;

        for (var index = 0;
             index < distance.Length;
             index++)
        {
            distance[index] =
                component.Membership[index]
                    ? infinity
                    : 0f;
        }

        for (var y = 0; y < height; y++)
        {
            for (var x = 0; x < width; x++)
            {
                var index = y * width + x;

                if (!component.Membership[index])
                {
                    continue;
                }

                var best = distance[index];

                if (x > 0)
                {
                    best = MathF.Min(
                        best,
                        distance[index - 1] + 1f);
                }

                if (y > 0)
                {
                    best = MathF.Min(
                        best,
                        distance[index - width] + 1f);

                    if (x > 0)
                    {
                        best = MathF.Min(
                            best,
                            distance[index - width - 1] +
                            Diagonal);
                    }

                    if (x + 1 < width)
                    {
                        best = MathF.Min(
                            best,
                            distance[index - width + 1] +
                            Diagonal);
                    }
                }

                distance[index] = best;
            }
        }

        for (var y = height - 1;
             y >= 0;
             y--)
        {
            for (var x = width - 1;
                 x >= 0;
                 x--)
            {
                var index = y * width + x;

                if (!component.Membership[index])
                {
                    continue;
                }

                var best = distance[index];

                if (x + 1 < width)
                {
                    best = MathF.Min(
                        best,
                        distance[index + 1] + 1f);
                }

                if (y + 1 < height)
                {
                    best = MathF.Min(
                        best,
                        distance[index + width] + 1f);

                    if (x + 1 < width)
                    {
                        best = MathF.Min(
                            best,
                            distance[index + width + 1] +
                            Diagonal);
                    }

                    if (x > 0)
                    {
                        best = MathF.Min(
                            best,
                            distance[index + width - 1] +
                            Diagonal);
                    }
                }

                distance[index] = best;
            }
        }

        return distance;
    }

    public static float Sample(
        float[] distance,
        Component component,
        PixelPoint point)
    {
        var x = Math.Clamp(
            (int)MathF.Round(point.X),
            0,
            component.CanvasWidth - 1);

        var y = Math.Clamp(
            (int)MathF.Round(point.Y),
            0,
            component.CanvasHeight - 1);

        return distance[
            y * component.CanvasWidth + x];
    }
}
