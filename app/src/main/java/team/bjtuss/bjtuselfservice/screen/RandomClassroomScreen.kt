package team.bjtuss.bjtuselfservice.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import team.bjtuss.bjtuselfservice.utils.ClassroomPlanner
import team.bjtuss.bjtuselfservice.viewmodel.MainViewModel
import team.bjtuss.bjtuselfservice.web.ClassroomCapacityService
import team.bjtuss.bjtuselfservice.web.ClassroomCapacityService.ClassroomCapacity
import team.bjtuss.bjtuselfservice.web.ClassroomCapacityService.getClassroomCapacity
import java.util.concurrent.TimeUnit
import kotlin.random.Random

private data class RandomRoom(
    val building: String,
    val classroom: ClassroomCapacity,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RandomClassroomScreen(
    navController: NavController,
    mainViewModel: MainViewModel,
) {
    val classroomViewModel = mainViewModel.classroomViewModel
    val classroomMap by classroomViewModel.classroomMap.collectAsState()
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedCampus by rememberSaveable { mutableStateOf("主区") }
    var pickedBuilding by rememberSaveable { mutableStateOf("") }
    var pickedRoom by rememberSaveable { mutableStateOf("") }
    var pickedCapacity by rememberSaveable { mutableStateOf(0) }
    var pickedUsed by rememberSaveable { mutableStateOf(0) }
    var hasPicked by rememberSaveable { mutableStateOf(false) }
    val result = if (hasPicked) {
        RandomRoom(
            pickedBuilding,
            ClassroomCapacity(pickedRoom, pickedCapacity, pickedUsed),
        )
    } else {
        null
    }

    suspend fun pick() {
        loading = true
        error = null
        withContext(Dispatchers.IO) {
            runCatching {
                if (classroomViewModel.classroomMap.value.isEmpty()) {
                    classroomViewModel.refreshClassroomMap()
                    delay(800)
                }
                val slot = getCurrentClassIndex()
                val map = classroomViewModel.classroomMap.value
                val candidates = map.filter { (room, slots) ->
                    room != "nowWeek" && !room.contains("|") &&
                        if (slot >= 0) slots.getOrNull(slot) == 0 else true
                }.keys.toList()
                if (candidates.isEmpty()) {
                    error = "当前没有可用的空闲教室"
                    return@runCatching
                }
                val shuffledBuildings = buildingsForCampus(selectedCampus).shuffled(Random.Default)
                for (building in shuffledBuildings.take(4)) {
                    val info = runCatching {
                        getClassroomCapacity(building).get(10, TimeUnit.SECONDS)
                    }.getOrNull() ?: continue
                    val freeRooms = info.ClassroomList.filter { classroom ->
                        classroom.RoomName in candidates && (
                            if (slot >= 0) map[classroom.RoomName]?.getOrNull(slot) == 0
                            else classroom.Capacity > 0 && classroom.Used < classroom.Capacity
                        )
                    }
                    if (freeRooms.isNotEmpty()) {
                        val picked = freeRooms.random(Random.Default)
                        pickedBuilding = building
                        pickedRoom = picked.RoomName
                        pickedCapacity = picked.Capacity
                        pickedUsed = picked.Used
                        hasPicked = true
                        return@runCatching
                    }
                }
                error = "暂时没有找到空闲教室，请稍后再试"
            }.onFailure {
                error = it.message ?: "随机教室加载失败"
            }
        }
        loading = false
    }

    fun switchCampus(campus: String) {
        if (selectedCampus == campus) return
        selectedCampus = campus
        hasPicked = false
        pickedBuilding = ""
        pickedRoom = ""
        pickedCapacity = 0
        pickedUsed = 0
        error = null
        scope.launch { pick() }
    }

    LaunchedEffect(Unit) {
        if (hasPicked) {
            loading = false
        } else {
            classroomViewModel.refreshClassroomMap()
            delay(1200)
            pick()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("随机自习", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Casino,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text("随机选一间当前空闲教室", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "当前节次：" + currentSlotLabel(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("随机范围", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (selectedCampus == "东区") "只从东区教学楼中抽取" else "只从主区教学楼中抽取",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilterChip(
                    selected = selectedCampus == "主区",
                    onClick = { switchCampus("主区") },
                    label = { Text("主区") },
                )
                FilterChip(
                    selected = selectedCampus == "东区",
                    onClick = { switchCampus("东区") },
                    label = { Text("东区") },
                )
            }

            when {
                loading -> Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null -> Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error)
                    Button(onClick = { scope.launch { pick() } }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("重试")
                    }
                }
                result != null -> {
                    val picked = result!!
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(picked.building, style = MaterialTheme.typography.titleMedium)
                            Text(
                                picked.classroom.RoomName,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                ClassroomPlanner.floorOf(picked.classroom.RoomName).toString() + " 层 · 容量 " +
                                    picked.classroom.Capacity + " 人",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "实时使用 " + picked.classroom.Used + "/" + picked.classroom.Capacity,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { scope.launch { pick() } }, modifier = Modifier.weight(1f)) {
                            Text("换一间")
                        }
                        Button(
                            onClick = {
                                navController.navigate(
                                    "ClassroomDetection/" + android.net.Uri.encode(picked.building) +
                                        "?room=" + android.net.Uri.encode(picked.classroom.RoomName)
                                )
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Apartment, contentDescription = null)
                            Text("查看该楼", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun currentSlotLabel(): String {
    val index = getCurrentClassIndex()
    return if (index >= 0) "第" + (index + 1) + "节" else "课间"
}

private val MAIN_BUILDINGS = listOf(
    "第十七号教学楼",
    "思源楼",
    "思源西楼",
    "思源东楼",
    "第九教学楼",
    "第八教学楼",
    "第五教学楼",
    "逸夫教学楼",
    "机械楼",
)

private val EAST_BUILDINGS = listOf(
    "东区二教",
    "东区一教",
)

private fun buildingsForCampus(campus: String): List<String> =
    if (campus == "东区") EAST_BUILDINGS else MAIN_BUILDINGS
