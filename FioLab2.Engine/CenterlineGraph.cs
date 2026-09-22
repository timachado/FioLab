namespace FioLab2.Engine;

internal sealed record CenterlineGraphResult(
    IReadOnlyList<GraphNode> Nodes,
    IReadOnlyList<GraphEdge> Edges);

internal static class CenterlineGraph
{
    public static CenterlineGraphResult Build(
        Component component,
        bool[] skeleton)
    {
        var width = component.CanvasWidth;
        var height = component.CanvasHeight;

        var special = new HashSet<int>();

        for (var index = 0;
             index < skeleton.Length;
             index++)
        {
            if (!skeleton[index])
            {
                continue;
            }

            var degree = SkeletonNeighbors(
                index,
                width,
                height,
                skeleton).Count;

            if (degree != 2)
            {
                special.Add(index);
            }
        }

        var nodes = BuildNodes(
            special,
            skeleton,
            width,
            height);

        var nodeByPixel =
            new Dictionary<int, int>();

        foreach (var node in nodes)
        {
            foreach (var pixel in node.Pixels)
            {
                nodeByPixel[pixel] =
                    node.Id;
            }
        }

        var visitedLinks =
            new HashSet<ulong>();

        var edges =
            new List<GraphEdge>();

        foreach (var node in nodes)
        {
            foreach (var pixel in node.Pixels)
            {
                foreach (var neighbor in SkeletonNeighbors(
                    pixel,
                    width,
                    height,
                    skeleton))
                {
                    if (
                        nodeByPixel.TryGetValue(
                            neighbor,
                            out var neighborNode) &&
                        neighborNode == node.Id)
                    {
                        continue;
                    }

                    var key =
                        Geometry.LinkKey(
                            pixel,
                            neighbor);

                    if (!visitedLinks.Add(key))
                    {
                        continue;
                    }

                    var path =
                        new List<PixelPoint>
                        {
                            node.Center
                        };

                    var previous = pixel;
                    var current = neighbor;
                    var endNodeId = -1;
                    var guard = 0;

                    while (
                        guard++ <
                        skeleton.Length)
                    {
                        if (
                            nodeByPixel.TryGetValue(
                                current,
                                out endNodeId))
                        {
                            path.Add(
                                nodes[endNodeId].Center);

                            break;
                        }

                        path.Add(
                            Geometry.IndexToPoint(
                                current,
                                width));

                        var nextCandidates =
                            SkeletonNeighbors(
                                current,
                                width,
                                height,
                                skeleton)
                            .Where(candidate =>
                                candidate != previous)
                            .ToList();

                        if (nextCandidates.Count == 0)
                        {
                            break;
                        }

                        var next =
                            nextCandidates.FirstOrDefault(
                                candidate =>
                                    !visitedLinks.Contains(
                                        Geometry.LinkKey(
                                            current,
                                            candidate)));

                        if (
                            next == 0 &&
                            !nextCandidates.Contains(0))
                        {
                            next =
                                nextCandidates[0];
                        }

                        var nextKey =
                            Geometry.LinkKey(
                                current,
                                next);

                        if (
                            visitedLinks.Contains(
                                nextKey) &&
                            !nodeByPixel.ContainsKey(next))
                        {
                            break;
                        }

                        visitedLinks.Add(nextKey);
                        previous = current;
                        current = next;
                    }

                    AddEdgeIfValid(
                        edges,
                        node.Id,
                        endNodeId,
                        path,
                        isLoop: false);
                }
            }
        }

        TraceResidualLoops(
            skeleton,
            width,
            height,
            nodeByPixel,
            visitedLinks,
            edges);

        return new CenterlineGraphResult(
            nodes,
            edges);
    }

    public static List<GraphEdge> PruneMicroSpurs(
        CenterlineGraphResult graph,
        float[] distance,
        Component component)
    {
        var nodes = graph.Nodes
            .ToDictionary(
                static node => node.Id);

        var result =
            new List<GraphEdge>();

        foreach (var edge in graph.Edges)
        {
            if (edge.IsLoop)
            {
                result.Add(edge);
                continue;
            }

            var start =
                edge.StartNodeId >= 0
                    ? nodes[edge.StartNodeId]
                    : null;

            var end =
                edge.EndNodeId >= 0
                    ? nodes[edge.EndNodeId]
                    : null;

            var startJunction =
                start?.IsJunction == true;

            var endJunction =
                end?.IsJunction == true;

            if (!(startJunction ^ endJunction))
            {
                result.Add(edge);
                continue;
            }

            var samples = edge.Points
                .Select(point =>
                    DistanceTransform.Sample(
                        distance,
                        component,
                        point))
                .Where(static radius =>
                    radius > 0f)
                .OrderBy(static radius =>
                    radius)
                .ToArray();

            if (samples.Length == 0)
            {
                result.Add(edge);
                continue;
            }

            var medianRadius =
                samples[
                    samples.Length / 2];

            var localDiameter =
                medianRadius * 2f;

            var threshold =
                MathF.Max(
                    4f,
                    localDiameter * 1.05f);

            if (edge.Length < threshold)
            {
                continue;
            }

            result.Add(edge);
        }

        return result;
    }

    private static List<GraphNode> BuildNodes(
        HashSet<int> special,
        bool[] skeleton,
        int width,
        int height)
    {
        var remaining =
            new HashSet<int>(special);

        var nodes =
            new List<GraphNode>();

        var queue =
            new Queue<int>();

        while (remaining.Count > 0)
        {
            var seed =
                remaining.First();

            remaining.Remove(seed);
            queue.Enqueue(seed);

            var cluster =
                new List<int>();

            while (queue.Count > 0)
            {
                var current =
                    queue.Dequeue();

                cluster.Add(current);

                foreach (var neighbor in Geometry.Neighbors(
                    current,
                    width,
                    height))
                {
                    if (
                        !special.Contains(neighbor) ||
                        !remaining.Remove(neighbor))
                    {
                        continue;
                    }

                    queue.Enqueue(neighbor);
                }
            }

            var centerX =
                cluster.Average(index =>
                    index % width);

            var centerY =
                cluster.Average(index =>
                    index / width);

            var junction =
                cluster.Any(index =>
                    SkeletonNeighbors(
                        index,
                        width,
                        height,
                        skeleton).Count >= 3);

            nodes.Add(
                new GraphNode(
                    nodes.Count,
                    new PixelPoint(
                        (float)centerX,
                        (float)centerY),
                    junction,
                    cluster));
        }

        return nodes;
    }

    private static List<int> SkeletonNeighbors(
        int index,
        int width,
        int height,
        bool[] skeleton)
    {
        // Topological 8-neighborhood without diagonal shortcuts.
        //
        // A rasterized curve frequently looks like a staircase. If a
        // diagonal pixel is counted in addition to an orthogonal bridge,
        // the middle stair pixel appears to have degree 3 or 4 and becomes
        // a fake junction. That was the main source of the 129 objects seen
        // in the Alliby "Maria" test.
        //
        // Keep a diagonal link only when neither orthogonal bridge exists.
        var result =
            new List<int>(8);

        var x = index % width;
        var y = index / width;

        static bool Inside(
            int x,
            int y,
            int width,
            int height) =>
            x >= 0 &&
            y >= 0 &&
            x < width &&
            y < height;

        bool Has(
            int px,
            int py)
        {
            if (!Inside(
                px,
                py,
                width,
                height))
            {
                return false;
            }

            return skeleton[
                py * width + px];
        }

        ReadOnlySpan<(int X, int Y)> cardinals =
        [
            ( 0, -1),
            ( 1,  0),
            ( 0,  1),
            (-1,  0)
        ];

        foreach (var offset in cardinals)
        {
            var nx = x + offset.X;
            var ny = y + offset.Y;

            if (Has(nx, ny))
            {
                result.Add(
                    ny * width + nx);
            }
        }

        ReadOnlySpan<(int X, int Y)> diagonals =
        [
            (-1, -1),
            ( 1, -1),
            ( 1,  1),
            (-1,  1)
        ];

        foreach (var offset in diagonals)
        {
            var nx = x + offset.X;
            var ny = y + offset.Y;

            if (!Has(nx, ny))
            {
                continue;
            }

            var horizontalBridge =
                Has(
                    x + offset.X,
                    y);

            var verticalBridge =
                Has(
                    x,
                    y + offset.Y);

            if (
                horizontalBridge ||
                verticalBridge)
            {
                continue;
            }

            result.Add(
                ny * width + nx);
        }

        return result;
    }

    private static void TraceResidualLoops(
        bool[] skeleton,
        int width,
        int height,
        IReadOnlyDictionary<int, int> nodeByPixel,
        HashSet<ulong> visitedLinks,
        List<GraphEdge> edges)
    {
        for (var index = 0;
             index < skeleton.Length;
             index++)
        {
            if (
                !skeleton[index] ||
                nodeByPixel.ContainsKey(index))
            {
                continue;
            }

            foreach (var neighbor in SkeletonNeighbors(
                index,
                width,
                height,
                skeleton))
            {
                if (
                    nodeByPixel.ContainsKey(neighbor))
                {
                    continue;
                }

                var key =
                    Geometry.LinkKey(
                        index,
                        neighbor);

                if (!visitedLinks.Add(key))
                {
                    continue;
                }

                var path =
                    new List<PixelPoint>
                    {
                        Geometry.IndexToPoint(
                            index,
                            width),
                        Geometry.IndexToPoint(
                            neighbor,
                            width)
                    };

                var start = index;
                var previous = index;
                var current = neighbor;
                var closed = false;
                var guard = 0;

                while (
                    guard++ <
                    skeleton.Length)
                {
                    var candidates =
                        SkeletonNeighbors(
                            current,
                            width,
                            height,
                            skeleton)
                        .Where(candidate =>
                            candidate != previous)
                        .ToList();

                    if (candidates.Count == 0)
                    {
                        break;
                    }

                    var next =
                        candidates.FirstOrDefault(
                            candidate =>
                                candidate == start ||
                                !visitedLinks.Contains(
                                    Geometry.LinkKey(
                                        current,
                                        candidate)));

                    if (
                        next == 0 &&
                        !candidates.Contains(0))
                    {
                        break;
                    }

                    var nextKey =
                        Geometry.LinkKey(
                            current,
                            next);

                    if (
                        next != start &&
                        !visitedLinks.Add(
                            nextKey))
                    {
                        break;
                    }

                    if (next == start)
                    {
                        visitedLinks.Add(nextKey);
                        path.Add(
                            Geometry.IndexToPoint(
                                start,
                                width));

                        closed = true;
                        break;
                    }

                    path.Add(
                        Geometry.IndexToPoint(
                            next,
                            width));

                    previous = current;
                    current = next;
                }

                if (closed)
                {
                    AddEdgeIfValid(
                        edges,
                        -1,
                        -1,
                        path,
                        isLoop: true);
                }
            }
        }
    }

    private static void AddEdgeIfValid(
        List<GraphEdge> edges,
        int startNodeId,
        int endNodeId,
        IReadOnlyList<PixelPoint> points,
        bool isLoop)
    {
        if (points.Count < 2)
        {
            return;
        }

        var length =
            Geometry.PolylineLength(
                points);

        if (length < 1.5f)
        {
            return;
        }

        edges.Add(
            new GraphEdge(
                edges.Count,
                startNodeId,
                endNodeId,
                points.ToList(),
                length,
                isLoop));
    }
}
