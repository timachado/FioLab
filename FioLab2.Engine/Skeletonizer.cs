namespace FioLab2.Engine;

internal static class Skeletonizer
{
    public static bool[] Thin(
        Component component)
    {
        var skeleton =
            new bool[component.Membership.Length];

        Array.Copy(
            component.Membership,
            skeleton,
            skeleton.Length);

        var width = component.CanvasWidth;
        var height = component.CanvasHeight;
        var changed = true;
        var iterations = 0;
        var toDelete = new List<int>();

        while (
            changed &&
            iterations < 256)
        {
            changed = false;
            iterations++;

            for (var phase = 0;
                 phase < 2;
                 phase++)
            {
                toDelete.Clear();

                for (var y = Math.Max(
                         1,
                         component.MinY);
                     y <= Math.Min(
                         height - 2,
                         component.MaxY);
                     y++)
                {
                    for (var x = Math.Max(
                             1,
                             component.MinX);
                         x <= Math.Min(
                             width - 2,
                             component.MaxX);
                         x++)
                    {
                        var index =
                            y * width + x;

                        if (!skeleton[index])
                        {
                            continue;
                        }

                        var p2 =
                            skeleton[
                                (y - 1) * width + x];

                        var p3 =
                            skeleton[
                                (y - 1) * width + x + 1];

                        var p4 =
                            skeleton[
                                y * width + x + 1];

                        var p5 =
                            skeleton[
                                (y + 1) * width + x + 1];

                        var p6 =
                            skeleton[
                                (y + 1) * width + x];

                        var p7 =
                            skeleton[
                                (y + 1) * width + x - 1];

                        var p8 =
                            skeleton[
                                y * width + x - 1];

                        var p9 =
                            skeleton[
                                (y - 1) * width + x - 1];

                        var neighbors =
                            BoolInt(p2) +
                            BoolInt(p3) +
                            BoolInt(p4) +
                            BoolInt(p5) +
                            BoolInt(p6) +
                            BoolInt(p7) +
                            BoolInt(p8) +
                            BoolInt(p9);

                        if (
                            neighbors < 2 ||
                            neighbors > 6)
                        {
                            continue;
                        }

                        var transitions =
                            CountTransitions(
                                p2,
                                p3,
                                p4,
                                p5,
                                p6,
                                p7,
                                p8,
                                p9);

                        if (transitions != 1)
                        {
                            continue;
                        }

                        var preserve =
                            phase == 0
                                ? p2 && p4 && p6 ||
                                  p4 && p6 && p8
                                : p2 && p4 && p8 ||
                                  p2 && p6 && p8;

                        if (!preserve)
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

    private static int BoolInt(
        bool value) =>
        value ? 1 : 0;

    private static int CountTransitions(
        params bool[] neighbors)
    {
        var transitions = 0;

        for (var index = 0;
             index < neighbors.Length;
             index++)
        {
            var current =
                neighbors[index];

            var next =
                neighbors[
                    (index + 1) %
                    neighbors.Length];

            if (!current && next)
            {
                transitions++;
            }
        }

        return transitions;
    }
}
