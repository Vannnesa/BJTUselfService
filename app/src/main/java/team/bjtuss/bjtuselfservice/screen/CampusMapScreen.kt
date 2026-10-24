package team.bjtuss.bjtuselfservice.screen

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.json.JSONObject
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.min

data class CampusPoi(
    val name: String,
    val x: Float,
    val y: Float,
    val type: String,
    val category: String,
    val url: String,
)

data class CampusMapData(
    val image: String,
    val width: Int,
    val height: Int,
    val originX: Float,
    val originY: Float,
    val unitsPerPixel: Float,
    val pois: List<CampusPoi>,
)

internal fun loadCampusMap(context: android.content.Context): CampusMapData? = runCatching {
    val json = context.assets.open("campus_map/campus_map.json")
        .bufferedReader(Charsets.UTF_8)
        .use { JSONObject(it.readText()) }
    val array = json.getJSONArray("pois")
    val pois = buildList {
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            add(
                CampusPoi(
                    name = item.optString("name"),
                    x = item.optDouble("x").toFloat(),
                    y = item.optDouble("y").toFloat(),
                    type = item.optString("type", "1"),
                    category = item.optString("category", "服务"),
                    url = item.optString("url"),
                )
            )
        }
    }
    CampusMapData(
        image = json.getString("image"),
        width = json.getInt("width"),
        height = json.getInt("height"),
        originX = json.getDouble("originX").toFloat(),
        originY = json.getDouble("originY").toFloat(),
        unitsPerPixel = json.getDouble("unitsPerPixel").toFloat().coerceAtLeast(1f),
        pois = pois,
    )
}.getOrNull()

private fun CampusMapData.pixelX(poi: CampusPoi): Float =
    (poi.x - originX) / unitsPerPixel

private fun CampusMapData.pixelY(poi: CampusPoi): Float =
    (poi.y - originY) / unitsPerPixel

internal fun categoryColor(category: String): Color = when (category) {
    "餐饮" -> Color(0xFFEF6C00)
    "宿舍" -> Color(0xFF6A1B9A)
    "运动" -> Color(0xFF2E7D32)
    "出入口" -> Color(0xFF37474F)
    "服务" -> Color(0xFF00838F)
    else -> Color(0xFF1565C0)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusMapScreen(navController: NavController) {
    val context = LocalContext.current
    val data = remember { loadCampusMap(context) }
    val bitmap = remember(data) {
        data?.let {
            runCatching {
                context.assets.open("campus_map/" + it.image).use { stream ->
                    BitmapFactory.decodeStream(stream).asImageBitmap()
                }
            }.getOrNull()
        }
    }

    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("全部") }
    var selectedName by rememberSaveable { mutableStateOf("") }
    var showNavigationDialog by rememberSaveable { mutableStateOf(false) }
    var startName by rememberSaveable { mutableStateOf("") }
    var endName by rememberSaveable { mutableStateOf("") }
    var routePoints by remember { mutableStateOf<List<Offset>?>(null) }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val currentZoom by rememberUpdatedState(zoom)
    val currentPan by rememberUpdatedState(pan)

    val selected = data?.pois?.firstOrNull { it.name == selectedName }
    val filtered = data?.pois.orEmpty().filter { poi ->
        (selectedCategory == "全部" || poi.category == selectedCategory) &&
            (query.isBlank() || poi.name.contains(query, ignoreCase = true))
    }
    val categories = listOf("全部") + (data?.pois.orEmpty().map { it.category }.distinct().sorted())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("校园地图", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text("搜索建筑、食堂、宿舍、校门") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(categories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(
                    onClick = { showNavigationDialog = true },
                    label = { Text("路线导航") },
                )
                if (startName.isNotBlank() && endName.isNotBlank()) {
                    Text(
                        text = startName + " → " + endName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (routePoints != null) {
                    AssistChip(
                        onClick = {
                            routePoints = null
                            startName = ""
                            endName = ""
                        },
                        label = { Text("清除路线") },
                    )
                }
            }
            if (query.isNotBlank() && filtered.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filtered) { poi ->
                        AssistChip(
                            onClick = {
                                selectedName = poi.name
                                if (data != null && viewport.width > 0) {
                                    val total = fitScale(data, viewport) * zoom
                                    pan = Offset(
                                        -((data.pixelX(poi) - data.width / 2f) * total),
                                        -((data.pixelY(poi) - data.height / 2f) * total),
                                    )
                                }
                            },
                            label = { Text(poi.name) },
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                if (data == null || bitmap == null) {
                    Text(
                        "地图数据加载失败",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .onSizeChanged { viewport = it }
                            .pointerInput(Unit) {
                                detectTransformGestures { _, panChange, zoomChange, _ ->
                                    zoom = (zoom * zoomChange).coerceIn(1f, 6f)
                                    pan += panChange
                                }
                            }
                            .pointerInput(data, viewport) {
                                detectTapGestures { tap ->
                                    val total = fitScale(data, viewport) * currentZoom
                                    if (total <= 0f) return@detectTapGestures
                                    val origin = Offset(
                                        (viewport.width - data.width * total) / 2f + currentPan.x,
                                        (viewport.height - data.height * total) / 2f + currentPan.y,
                                    )
                                    val tappedMap = (tap - origin) / total
                                    val nearest = data.pois
                                        .filter { it.category == selectedCategory || selectedCategory == "全部" }
                                        .minByOrNull { poi ->
                                            hypot(
                                                data.pixelX(poi) - tappedMap.x,
                                                data.pixelY(poi) - tappedMap.y,
                                            )
                                        }
                                    if (nearest != null && hypot(
                                            data.pixelX(nearest) - tappedMap.x,
                                            data.pixelY(nearest) - tappedMap.y,
                                        ) * total < 28f
                                    ) {
                                        selectedName = nearest.name
                                    }
                                }
                            }
                    ) {
                        val total = fitScale(data, viewport) * zoom
                        val origin = Offset(
                            (size.width - data.width * total) / 2f + pan.x,
                            (size.height - data.height * total) / 2f + pan.y,
                        )
                        withTransform({
                            translate(origin.x, origin.y)
                            scale(total, total, pivot = Offset.Zero)
                        }) {
                            drawImage(bitmap)
                            routePoints?.takeIf { it.size > 1 }?.let { route ->
                                val routePath = Path().apply {
                                    moveTo(route.first().x, route.first().y)
                                    route.drop(1).forEach { lineTo(it.x, it.y) }
                                }
                                drawPath(
                                    path = routePath,
                                    color = Color.White,
                                    style = Stroke(
                                        width = 8f / total,
                                        cap = StrokeCap.Round,
                                    ),
                                )
                                drawPath(
                                    path = routePath,
                                    color = Color(0xFFE65100),
                                    style = Stroke(
                                        width = 4.5f / total,
                                        cap = StrokeCap.Round,
                                    ),
                                )
                                drawCircle(
                                    color = Color(0xFF2E7D32),
                                    radius = 7f / total,
                                    center = route.first(),
                                )
                                drawCircle(
                                    color = Color(0xFFC62828),
                                    radius = 7f / total,
                                    center = route.last(),
                                )
                            }
                            data.pois.forEach { poi ->
                                if (selectedCategory != "全部" && poi.category != selectedCategory) return@forEach
                                val point = Offset(data.pixelX(poi), data.pixelY(poi))
                                val isSelected = poi.name == selectedName
                                drawCircle(
                                    color = Color.White,
                                    radius = (if (isSelected) 9f else 6f) / total,
                                    center = point,
                                )
                                drawCircle(
                                    color = categoryColor(poi.category),
                                    radius = (if (isSelected) 6f else 4f) / total,
                                    center = point,
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        IconButton(
                            onClick = { zoom = (zoom * 1.25f).coerceAtMost(6f) },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)),
                        ) { Icon(Icons.Default.Add, contentDescription = "放大") }
                        IconButton(
                            onClick = { zoom = (zoom / 1.25f).coerceAtLeast(1f) },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)),
                        ) { Icon(Icons.Default.Remove, contentDescription = "缩小") }
                    }
                }
            }

            if (selected != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(selected.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(selected.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            data?.pois?.count { it.category == selected.category }.toString() + " 个同类地点",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    if (showNavigationDialog && data != null) {
        NavigationDialog(
            pois = data.pois,
            startName = startName,
            endName = endName,
            onDismiss = { showNavigationDialog = false },
            onClear = {
                routePoints = null
                startName = ""
                endName = ""
                showNavigationDialog = false
            },
            onPlan = { start, end ->
                val startPoint = Offset(data.pixelX(start), data.pixelY(start))
                val endPoint = Offset(data.pixelX(end), data.pixelY(end))
                routePoints = CampusRoutePlanner.route(startPoint, endPoint)
                startName = start.name
                endName = end.name
                selectedName = end.name
                zoom = 1f
                pan = Offset.Zero
                showNavigationDialog = false
            },
        )
    }
}

@Composable
private fun NavigationDialog(
    pois: List<CampusPoi>,
    startName: String,
    endName: String,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onPlan: (CampusPoi, CampusPoi) -> Unit,
) {
    var start by remember { mutableStateOf(pois.firstOrNull { it.name == startName }) }
    var end by remember { mutableStateOf(pois.firstOrNull { it.name == endName }) }
    var pickerTarget by remember { mutableStateOf<PickerTarget?>(null) }

    if (pickerTarget != null) {
        PoiPickerDialog(
            title = if (pickerTarget == PickerTarget.START) "选择起点" else "选择终点",
            pois = pois,
            onSelect = { poi ->
                if (pickerTarget == PickerTarget.START) start = poi else end = poi
                pickerTarget = null
            },
            onDismiss = { pickerTarget = null },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("路线导航") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("起点", style = MaterialTheme.typography.labelLarge)
                    OutlinedButton(
                        onClick = { pickerTarget = PickerTarget.START },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = start?.name ?: "选择起点",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text("终点", style = MaterialTheme.typography.labelLarge)
                    OutlinedButton(
                        onClick = { pickerTarget = PickerTarget.END },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = end?.name ?: "选择终点",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        "路线会沿校园主要道路绘制，转弯点为示意位置。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (start != null && end != null) onPlan(start!!, end!!)
                    }
                ) {
                    Text("规划路线")
                }
            },
            dismissButton = {
                TextButton(onClick = onClear) { Text("清除") }
            },
        )
    }
}

private enum class PickerTarget { START, END }

@Composable
private fun PoiPickerDialog(
    title: String,
    pois: List<CampusPoi>,
    onSelect: (CampusPoi) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = pois.filter {
        query.isBlank() || it.name.contains(query, ignoreCase = true)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("搜索地点") },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(filtered, key = { it.name }) { poi ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(poi) }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                        ) {
                            Column {
                                Text(poi.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    poi.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

private fun fitScale(data: CampusMapData, viewport: IntSize): Float {
    if (viewport.width <= 0 || viewport.height <= 0) return 1f
    return min(
        viewport.width.toFloat() / data.width,
        viewport.height.toFloat() / data.height,
    ).coerceAtLeast(0.01f)
}
