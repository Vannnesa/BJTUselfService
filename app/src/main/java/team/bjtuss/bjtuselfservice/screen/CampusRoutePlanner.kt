package team.bjtuss.bjtuselfservice.screen

import androidx.compose.ui.geometry.Offset
import java.util.PriorityQueue
import kotlin.math.hypot

/**
 * Roads are digitized from the road-name labels printed on the campus map:
 * 知行大道, 银杏大道, 红果园路, 传习路, 积秀路 and the east-campus loop.
 * Coordinates are map-image pixels, so this layer and the raster map align.
 */
object CampusRoutePlanner {
    private data class Road(val name: String, val points: List<Offset>)

    private val roads = listOf(
        Road(
            "知行大道",
            listOf(
                Offset(900f, 1260f),
                Offset(1100f, 1220f),
                Offset(1300f, 1160f),
                Offset(1500f, 1080f),
                Offset(1700f, 1010f),
                Offset(1900f, 980f),
                Offset(2050f, 970f),
            ),
        ),
        Road(
            "银杏大道",
            listOf(
                Offset(1300f, 1160f),
                Offset(1280f, 1350f),
                Offset(1260f, 1500f),
                Offset(1240f, 1650f),
            ),
        ),
        Road(
            "红果园路",
            listOf(
                Offset(1700f, 1010f),
                Offset(1750f, 1120f),
                Offset(1800f, 1250f),
                Offset(1850f, 1400f),
                Offset(1850f, 1450f),
                Offset(1880f, 1550f),
            ),
        ),
        Road(
            "传习路",
            listOf(
                Offset(1100f, 1520f),
                Offset(1300f, 1510f),
                Offset(1260f, 1500f),
                Offset(1500f, 1490f),
                Offset(1700f, 1460f),
                Offset(1850f, 1450f),
            ),
        ),
        Road(
            "积秀路",
            listOf(
                Offset(850f, 1600f),
                Offset(1050f, 1560f),
                Offset(1260f, 1500f),
            ),
        ),
        Road(
            "明光路",
            listOf(
                Offset(780f, 1260f),
                Offset(1000f, 1360f),
                Offset(1100f, 1450f),
            ),
        ),
        Road(
            "南门路",
            listOf(
                Offset(1880f, 1550f),
                Offset(1899f, 1727f),
            ),
        ),
        Road(
            "西门路",
            listOf(
                Offset(850f, 1600f),
                Offset(650f, 1700f),
                Offset(531f, 1738f),
            ),
        ),
        Road(
            "东门路",
            listOf(
                Offset(2050f, 970f),
                Offset(2250f, 800f),
                Offset(2558f, 678f),
            ),
        ),
        Road(
            "东校区连接路",
            listOf(
                Offset(2558f, 678f),
                Offset(2700f, 900f),
                Offset(2800f, 1180f),
            ),
        ),
        Road(
            "东校区环路",
            listOf(
                Offset(2800f, 1180f),
                Offset(3000f, 1120f),
                Offset(3300f, 1160f),
                Offset(3600f, 1200f),
                Offset(3800f, 1170f),
            ),
        ),
        Road(
            "东校区纵路",
            listOf(
                Offset(3300f, 820f),
                Offset(3300f, 1160f),
                Offset(3300f, 2040f),
            ),
        ),
    )

    private val nodes = mutableListOf<Offset>()
    private val edges = mutableListOf<Pair<Int, Int>>()

    init {
        roads.forEach { road ->
            var previous = -1
            road.points.forEach { point ->
                var index = nodes.indexOf(point)
                if (index < 0) {
                    nodes += point
                    index = nodes.lastIndex
                }
                if (previous >= 0) edges += previous to index
                previous = index
            }
        }
    }

    fun roads(): List<Pair<String, List<Offset>>> = roads.map { it.name to it.points }

    fun roadSegments(): List<Pair<Offset, Offset>> =
        edges.map { (a, b) -> nodes[a] to nodes[b] }

    fun route(start: Offset, end: Offset): List<Offset> {
        val points = nodes.toMutableList()
        val startIndex = points.size
        points += start
        val endIndex = points.size
        points += end
        val adjacency = MutableList(points.size) { mutableListOf<Pair<Int, Float>>() }
        fun addEdge(a: Int, b: Int) {
            val weight = distance(points[a], points[b])
            adjacency[a].add(b to weight)
            adjacency[b].add(a to weight)
        }
        edges.forEach { (a, b) -> addEdge(a, b) }
        addEdge(startIndex, nearestNode(start))
        addEdge(endIndex, nearestNode(end))

        val previous = IntArray(points.size) { -1 }
        val best = FloatArray(points.size) { Float.MAX_VALUE }
        best[startIndex] = 0f
        val queue = PriorityQueue<Pair<Int, Float>>(compareBy { it.second })
        queue.add(startIndex to 0f)
        while (queue.isNotEmpty()) {
            val (node, cost) = queue.remove()
            if (cost > best[node]) continue
            if (node == endIndex) break
            adjacency[node].forEach { (next, weight) ->
                val candidate = cost + weight
                if (candidate < best[next]) {
                    best[next] = candidate
                    previous[next] = node
                    queue.add(next to candidate)
                }
            }
        }
        if (best[endIndex] == Float.MAX_VALUE) return listOf(start, end)
        val path = mutableListOf<Int>()
        var cursor = endIndex
        while (cursor != -1) {
            path.add(cursor)
            cursor = previous[cursor]
        }
        path.reverse()
        return path.map { points[it] }
    }

    private fun nearestNode(point: Offset): Int =
        nodes.indices.minByOrNull { distance(nodes[it], point) } ?: 0

    private fun distance(a: Offset, b: Offset): Float = hypot(a.x - b.x, a.y - b.y)
}
