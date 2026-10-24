package team.bjtuss.bjtuselfservice.screen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private data class MapRegion(
    val key: String,
    val label: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val centerX get() = (left + right) / 2f
    val centerY get() = (top + bottom) / 2f
    val width get() = right - left
    val height get() = bottom - top
}

private data class MapRoad(val points: List<Offset>)

private val MAIN_REGION = MapRegion("main", "主校区", 0f, 0f, 2800f, 2304f)
private val EAST_REGION = MapRegion("east", "东校区", 2800f, 760f, 3840f, 2100f)

private val MAIN_ROADS = listOf(
    MapRoad(listOf(Offset(180f, 1180f), Offset(1370f, 1150f), Offset(2700f, 1170f))),
    MapRoad(listOf(Offset(1400f, 120f), Offset(1430f, 2180f))),
    MapRoad(listOf(Offset(420f, 430f), Offset(830f, 860f), Offset(1080f, 1200f))),
    MapRoad(listOf(Offset(2110f, 430f), Offset(2320f, 850f), Offset(2400f, 1480f))),
    MapRoad(listOf(Offset(620f, 1640f), Offset(1100f, 1430f), Offset(1700f, 1450f))),
)
private val EAST_ROADS = listOf(
    MapRoad(listOf(Offset(2820f, 1160f), Offset(3300f, 1150f), Offset(3800f, 1170f))),
    MapRoad(listOf(Offset(3300f, 820f), Offset(3310f, 2040f))),
    MapRoad(listOf(Offset(2880f, 1580f), Offset(3400f, 1480f), Offset(3780f, 1510f))),
)

private val MAJOR_LABELS = setOf(
    "思源楼", "思源西楼", "思源东楼", "图书馆", "第一教学楼", "第二教学楼",
    "第五教学楼", "第七教学楼", "第八教学楼", "第九教学楼", "逸夫教学楼",
    "科学会堂", "天佑会堂", "综合体育馆", "学生一食堂", "学苑食堂", "明湖",
    "南门", "东门", "西门", "主校区", "东校区",
)

private fun CampusMapData.poiPixelX(poi: CampusPoi) = (poi.x - originX) / unitsPerPixel
private fun CampusMapData.poiPixelY(poi: CampusPoi) = (poi.y - originY) / unitsPerPixel

private fun MapRegion.contains(data: CampusMapData, poi: CampusPoi): Boolean {
    val x = data.poiPixelX(poi)
    val y = data.poiPixelY(poi)
    return x in left..right && y in top..bottom
}

private fun regionFitScale(region: MapRegion, viewport: IntSize): Float {
    if (viewport.width <= 0 || viewport.height <= 0) return 1f
    return min(viewport.width / region.width, viewport.height / region.height)
        .coerceAtLeast(0.01f)
}

private fun rotationForGate(region: MapRegion, data: CampusMapData, gate: CampusPoi?): Float {
    if (gate == null) return 0f
    val dx = data.poiPixelX(gate) - region.centerX
    val dy = data.poiPixelY(gate) - region.centerY
    if (dx == 0f && dy == 0f) return 0f
    return (-90.0 - Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))).toFloat()
}

private fun rotateVector(offset: Offset, degrees: Float): Offset {
    val radians = Math.toRadians(degrees.toDouble())
    val c = cos(radians).toFloat()
    val s = sin(radians).toFloat()
    return Offset(offset.x * c - offset.y * s, offset.x * s + offset.y * c)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusMapProScreen(navController: NavController) {
    val context = LocalContext.current
    val data = remember { loadCampusMap(context) }
    val textMeasurer = rememberTextMeasurer()

    var areaKey by rememberSaveable { mutableStateOf("main") }
    var gateName by rememberSaveable { mutableStateOf("正北") }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedName by rememberSaveable { mutableStateOf("") }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val currentPan by rememberUpdatedState(pan)
    val currentZoom by rememberUpdatedState(zoom)

    val region = if (areaKey == "east") EAST_REGION else MAIN_REGION
    val regionPois = data?.pois.orEmpty().filter { data != null && region.contains(data, it) }
    val gates = regionPois.filter { it.name.contains("门") && it.name.length <= 10 }
        .map { it.name }.distinct()
    val gate = regionPois.firstOrNull { it.name == gateName }
    val desiredRotation = if (gateName == "正北" || data == null) 0f else {
        rotationForGate(region, data, gate)
    }
    var rotationTarget by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(areaKey, gateName, desiredRotation) {
        var delta = (desiredRotation - rotationTarget + 540f) % 360f - 180f
        if (delta == -180f) delta = 180f
        rotationTarget += delta
    }
    val rotation by animateFloatAsState(
        targetValue = rotationTarget,
        animationSpec = tween(650, easing = FastOutSlowInEasing),
        label = "campus-map-rotation",
    )
    val selected = regionPois.firstOrNull { it.name == selectedName }
    val filtered = regionPois.filter {
        query.isBlank() || it.name.contains(query, ignoreCase = true)
    }

    fun screenPoint(poi: CampusPoi): Offset {
        if (data == null) return Offset.Zero
        val total = regionFitScale(region, viewport) * zoom
        val relative = Offset(
            data.poiPixelX(poi) - region.centerX,
            data.poiPixelY(poi) - region.centerY,
        )
        return Offset(viewport.width / 2f + pan.x, viewport.height / 2f + pan.y) +
            rotateVector(relative, rotation) * total
    }

    fun centerOn(poi: CampusPoi) {
        if (data == null || viewport.width == 0) return
        val total = regionFitScale(region, viewport) * zoom
        val relative = Offset(
            data.poiPixelX(poi) - region.centerX,
            data.poiPixelY(poi) - region.centerY,
        )
        val rotated = rotateVector(relative, rotation)
        pan = Offset(-rotated.x * total, -rotated.y * total)
        selectedName = poi.name
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("校园地图 Pro", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = areaKey == "main",
                    onClick = {
                        areaKey = "main"
                        gateName = "正北"
                        selectedName = ""
                        pan = Offset.Zero
                        zoom = 1f
                    },
                    label = { Text("主校区") },
                )
                FilterChip(
                    selected = areaKey == "east",
                    onClick = {
                        areaKey = "east"
                        gateName = "正北"
                        selectedName = ""
                        pan = Offset.Zero
                        zoom = 1f
                    },
                    label = { Text("东校区") },
                )
                Text(
                    text = region.label + " · 手绘示意图",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text("搜索当前校区地点") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = gateName == "正北",
                    onClick = { gateName = "正北" },
                    label = { Text("正北向上") },
                )
                gates.forEach { name ->
                    FilterChip(
                        selected = gateName == name,
                        onClick = {
                            gateName = name
                            selectedName = ""
                            pan = Offset.Zero
                        },
                        label = { Text(name + "向上") },
                    )
                }
            }

            if (query.isNotBlank() && filtered.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filtered) { poi ->
                        AssistChip(onClick = { centerOn(poi) }, label = { Text(poi.name) })
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (data == null) {
                    Text(
                        "地图数据加载失败",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { viewport = it }
                            .pointerInput(Unit) {
                                detectTransformGestures { _, panChange, zoomChange, _ ->
                                    zoom = (zoom * zoomChange).coerceIn(1f, 6f)
                                    pan += panChange
                                }
                            }
                            .pointerInput(data, region, rotation, viewport) {
                                detectTapGestures { tap ->
                                    val total = regionFitScale(region, viewport) * currentZoom
                                    if (total <= 0f) return@detectTapGestures
                                    val screenCenter = Offset(
                                        viewport.width / 2f + currentPan.x,
                                        viewport.height / 2f + currentPan.y,
                                    )
                                    val relative = (tap - screenCenter) / total
                                    val unrotated = rotateVector(relative, -rotation)
                                    val mapPoint = Offset(
                                        unrotated.x + region.centerX,
                                        unrotated.y + region.centerY,
                                    )
                                    val nearest = regionPois.minByOrNull { poi ->
                                        hypot(
                                            data.poiPixelX(poi) - mapPoint.x,
                                            data.poiPixelY(poi) - mapPoint.y,
                                        )
                                    }
                                    if (nearest != null && hypot(
                                            data.poiPixelX(nearest) - mapPoint.x,
                                            data.poiPixelY(nearest) - mapPoint.y,
                                        ) * total < 28f
                                    ) {
                                        selectedName = nearest.name
                                    }
                                }
                            }
                    ) {
                        drawRect(Color(0xFFF7F4EB))
                        val total = regionFitScale(region, viewport) * zoom
                        withTransform({
                            translate(size.width / 2f + pan.x, size.height / 2f + pan.y)
                            rotate(rotation, pivot = Offset.Zero)
                            scale(total, total, pivot = Offset.Zero)
                            translate(-region.centerX, -region.centerY)
                        }) {
                            // Soft paper-like campus area.
                            drawRoundRect(
                                color = Color(0xFFEDF3E5),
                                topLeft = Offset(region.left, region.top),
                                size = Size(region.width, region.height),
                                cornerRadius = CornerRadius(34f, 34f),
                            )
                            drawRoundRect(
                                color = Color(0xFF9BB58A),
                                topLeft = Offset(region.left, region.top),
                                size = Size(region.width, region.height),
                                cornerRadius = CornerRadius(34f, 34f),
                                style = Stroke(width = 2f / total),
                            )

                            // Roads.
                            val roads = if (region.key == "east") EAST_ROADS else MAIN_ROADS
                            roads.forEach { road ->
                                val path = Path().apply {
                                    val first = road.points.first()
                                    moveTo(first.x, first.y)
                                    road.points.drop(1).forEach { lineTo(it.x, it.y) }
                                }
                                drawPath(
                                    path = path,
                                    color = Color(0xFFFFFDF7),
                                    style = Stroke(width = 22f / total, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                                )
                                drawPath(
                                    path = path,
                                    color = Color(0xFFD8D0BE),
                                    style = Stroke(width = 1.5f / total, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                                )
                            }

                            // Lake (明湖), when present in this region.
                            regionPois.firstOrNull { it.name == "明湖" }?.let { lake ->
                                val cx = data.poiPixelX(lake)
                                val cy = data.poiPixelY(lake)
                                drawOval(
                                    color = Color(0xFFB9DAE8),
                                    topLeft = Offset(cx - 70f, cy - 34f),
                                    size = Size(140f, 68f),
                                )
                                drawOval(
                                    color = Color(0xFF7FB8CE),
                                    topLeft = Offset(cx - 70f, cy - 34f),
                                    size = Size(140f, 68f),
                                    style = Stroke(width = 1.5f / total),
                                )
                            }

                            regionPois.forEach { poi ->
                                if (poi.name == "明湖") return@forEach
                                if (poi.category == "服务" || poi.category == "出入口") return@forEach
                                val cx = data.poiPixelX(poi)
                                val cy = data.poiPixelY(poi)
                                val size = when (poi.category) {
                                    "宿舍" -> Size(30f, 18f)
                                    "餐饮" -> Size(30f, 20f)
                                    "运动" -> Size(50f, 26f)
                                    else -> Size(42f, 28f)
                                }
                                val color = categoryColor(poi.category)
                                drawRoundRect(
                                    color = color.copy(alpha = 0.30f),
                                    topLeft = Offset(cx - size.width / 2f, cy - size.height / 2f),
                                    size = size,
                                    cornerRadius = CornerRadius(3f, 3f),
                                )
                                drawRoundRect(
                                    color = color.copy(alpha = 0.78f),
                                    topLeft = Offset(cx - size.width / 2f, cy - size.height / 2f),
                                    size = size,
                                    cornerRadius = CornerRadius(3f, 3f),
                                    style = Stroke(width = 1.2f / total),
                                )
                            }

                            // Small POI dots and gates.
                            regionPois.forEach { poi ->
                                val point = Offset(data.poiPixelX(poi), data.poiPixelY(poi))
                                val selected = poi.name == selectedName
                                drawCircle(
                                    color = Color.White,
                                    radius = (if (selected) 8f else 4.5f) / total,
                                    center = point,
                                )
                                drawCircle(
                                    color = if (selected) Color(0xFFE53935) else categoryColor(poi.category),
                                    radius = (if (selected) 5f else 2.8f) / total,
                                    center = point,
                                )
                            }
                        }

                        // Labels are drawn outside the transform so text stays upright
                        // while the map itself rotates smoothly.
                        val labels = regionPois.filter {
                            (it.name in MAJOR_LABELS || it.name == selectedName) &&
                                (query.isBlank() || it.name.contains(query, ignoreCase = true))
                        }
                        labels.forEach { poi ->
                            val screen = screenPoint(poi)
                            if (screen.x !in -80f..(size.width + 80f) ||
                                screen.y !in -30f..(size.height + 30f)
                            ) return@forEach
                            val layout = textMeasurer.measure(
                                text = poi.name,
                                style = TextStyle(
                                    fontSize = if (poi.name == selectedName) 12.sp else 10.sp,
                                    color = Color(0xFF1E2A24),
                                    fontWeight = if (poi.name == selectedName) FontWeight.Bold else FontWeight.Medium,
                                ),
                            )
                            val padding = 3f
                            val topLeft = Offset(
                                screen.x - layout.size.width / 2f - padding,
                                screen.y - layout.size.height - 10f - padding,
                            )
                            drawRoundRect(
                                color = Color(0xFFFFFEF8).copy(alpha = 0.92f),
                                topLeft = topLeft,
                                size = Size(
                                    layout.size.width + padding * 2,
                                    layout.size.height + padding * 2,
                                ),
                                cornerRadius = CornerRadius(4f, 4f),
                            )
                            drawText(
                                textLayoutResult = layout,
                                topLeft = Offset(topLeft.x + padding, topLeft.y + padding),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        IconButton(
                            onClick = { zoom = (zoom * 1.25f).coerceAtMost(6f) },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)),
                        ) { Icon(Icons.Default.Add, contentDescription = "放大") }
                        IconButton(
                            onClick = { zoom = (zoom / 1.25f).coerceAtLeast(1f) },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)),
                        ) { Icon(Icons.Default.Remove, contentDescription = "缩小") }
                    }
                    Text(
                        text = "指北：正北向上" + if (gateName != "正北") " · 已转为" + gateName + "向上" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.BottomStart)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                }
            }

            if (selected != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(selected.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            selected.category + " · " + (if (areaKey == "east") "东校区" else "主校区"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
