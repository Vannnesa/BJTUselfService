package team.bjtuss.bjtuselfservice.screen

import androidx.compose.ui.geometry.Offset
import java.util.PriorityQueue
import kotlin.math.hypot

/**
 * A lightweight schematic road graph in map-image pixel coordinates.
 * It is intentionally simple: POIs connect to the nearest road node, then
 * Dijkstra finds a route along the hand-authored campus roads.
 */
object CampusRoutePlanner {
    private val roadNodes = listOf(
        Offset(200f, 1180f),
        Offset(1400f, 1180f),
        Offset(2800f, 1180f),
        Offset(3800f, 1170f),
        Offset(1400f, 120f),
        Offset(1400f, 2180f),
        Offset(420f, 430f),
        Offset(830f, 860f),
        Offset(1080f, 1200f),
        Offset(2110f, 430f),
        Offset(2320f, 850f),
        Offset(2400f, 1480f),
        Offset(620f, 1640f),
        Offset(1100f, 1430f),
        Offset(1700f, 1450f),
        Offset(3300f, 1160f),
        Offset(3300f, 820f),
        Offset(3300f, 2040f),
        Offset(2880f, 1580f),
        Offset(3400f, 1480f),
        Offset(3780f, 1510f),
    )

    private val roadEdges = listOf(
        0 to 1, 1 to 2, 2 to 3,
        4 to 1, 5 to 1,
        6 to 7, 7 to 8, 8 to 1,
        9 to 10, 10 to 11, 11 to 14, 14 to 2,
        12 to 13, 13 to 14,
        2 to 15, 15 to 3, 15 to 16, 15 to 17,
        17 to 18, 18 to 19, 19 to 20, 15 to 19,
    )

    fun roadSegments(): List<Pair<Offset, Offset>> =
        roadEdges.map { (a, b) -> roadNodes[a] to roadNodes[b] }

    fun route(start: Offset, end: Offset): List<Offset> {
        val points = roadNodes.toMutableList()
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
        roadEdges.forEach { (a, b) -> addEdge(a, b) }
        val startRoad = nearestNode(start)
        val endRoad = nearestNode(end)
        addEdge(startIndex, startRoad)
        addEdge(endIndex, endRoad)

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
        roadNodes.indices.minByOrNull { distance(roadNodes[it], point) } ?: 0

    private fun distance(a: Offset, b: Offset): Float =
        hypot(a.x - b.x, a.y - b.y)
}
