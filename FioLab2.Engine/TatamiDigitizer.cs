namespace FioLab2.Engine;

internal static class TatamiDigitizer
{
    public static List<StitchPoint> Build(
        Component component,
        RasterGlyph raster,
        int objectIndex,
        DigitizeOptions options)
    {
        var result =
            new List<StitchPoint>();

        var spacing =
            Math.Max(
                2,
                (int)MathF.Round(
                    options.TatamiRowSpacingPx *
                    raster.Scale));

        PixelPoint? previousEnd = null;
        var rowNumber = 0;

        for (var y = component.MinY;
             y <= component.MaxY;
             y += spacing)
        {
            var spans =
                FindSpans(
                    component,
                    y);

            if (spans.Count == 0)
            {
                continue;
            }

            var leftToRight =
                rowNumber % 2 == 0;

            var ordered =
                leftToRight
                    ? spans
                    : spans
                        .AsEnumerable()
                        .Reverse()
                        .ToList();

            foreach (var span in ordered)
            {
                var start =
                    leftToRight
                        ? new PixelPoint(
                            span.Start,
                            y)
                        : new PixelPoint(
                            span.End,
                            y);

                var end =
                    leftToRight
                        ? new PixelPoint(
                            span.End,
                            y)
                        : new PixelPoint(
                            span.Start,
                            y);

                var connectorIsSafe =
                    previousEnd is not null &&
                    Geometry.SegmentInside(
                        component,
                        previousEnd.Value,
                        start);

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

                result.Add(
                    new StitchPoint(
                        endWorld.X,
                        endWorld.Y,
                        StitchCommand.Stitch,
                        objectIndex));

                previousEnd = end;
            }

            rowNumber++;
        }

        return result;
    }

    private static List<(int Start, int End)> FindSpans(
        Component component,
        int y)
    {
        var result =
            new List<(int Start, int End)>();

        var x =
            component.MinX;

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
                x + 1 <= component.MaxX &&
                component.Contains(
                    x + 1,
                    y))
            {
                x++;
            }

            result.Add(
                (start, x));

            x++;
        }

        return result;
    }
}
