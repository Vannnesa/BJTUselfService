package team.bjtuss.bjtuselfservice.web;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import team.bjtuss.bjtuselfservice.BuildConfig;

/**
 * Reads the current classroom service (the same HTTPS service used by the
 * upstream app). The old yaya.csoci.com HTTP endpoint is no longer reachable.
 */
public class ClassroomCapacityService {
    public static boolean ok = false;
    private static final String BASE_URL = BuildConfig.FREEC_API_BASE_URL;
    private static final String TOKEN = BuildConfig.FREEC_APP_TOKEN;

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build();

    public static CompletableFuture<BuildingInfo> getClassroomCapacity(String buildingName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return loadBuilding(buildingName);
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        });
    }

    private static BuildingInfo loadBuilding(String buildingName) throws Exception {
        if (TOKEN == null || TOKEN.trim().isEmpty()) {
            throw new IOException("教室数据服务未配置，请安装包含服务令牌的版本");
        }
        int buildingId = findBuildingId(buildingName);
        String body = get("/api/v1/rooms?building_id=" + buildingId);
        JSONArray data = new JSONArray(body);
        List<ClassroomCapacity> rooms = new ArrayList<>();
        String observedAt = "";
        for (int i = 0; i < data.length(); i++) {
            JSONObject room = data.optJSONObject(i);
            if (room == null) continue;
            String roomName = room.optString("name", "");
            int capacity = room.optInt("capacity", 0);
            int used = 0;
            JSONObject occupancy = room.optJSONObject("occupancy");
            if (occupancy != null) {
                used = occupancy.optInt("count", 0);
                String observed = occupancy.optString("observed_at", "");
                if (!observed.isEmpty() && observedAt.isEmpty()) observedAt = observed;
            }
            if (!roomName.isEmpty()) {
                rooms.add(new ClassroomCapacity(roomName, capacity, used));
            }
        }
        String time = observedAt.replace("T", " ");
        if (time.endsWith("Z")) time = time.substring(0, time.length() - 1);
        return new BuildingInfo(buildingName, rooms, time, "");
    }

    private static int findBuildingId(String buildingName) throws Exception {
        String body = get("/api/v1/buildings");
        JSONArray data = new JSONArray(body);
        for (int i = 0; i < data.length(); i++) {
            JSONObject building = data.optJSONObject(i);
            if (building == null) continue;
            if (buildingName.equals(building.optString("name"))) {
                return building.optInt("id", -1);
            }
        }
        throw new IOException("教室服务中没有找到 " + buildingName);
    }

    private static String get(String path) throws Exception {
        String baseUrl = BASE_URL.endsWith("/")
                ? BASE_URL.substring(0, BASE_URL.length() - 1)
                : BASE_URL;
        Request request = new Request.Builder()
                .url(baseUrl + path)
                .header("Authorization", "Bearer " + TOKEN)
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("教室服务返回 HTTP " + response.code());
            }
            if (response.body() == null) {
                throw new IOException("教室服务返回空数据");
            }
            return response.body().string();
        }
    }

    public static class ClassroomCapacity {
        public String RoomName;
        public int Capacity;
        public int Used;

        public ClassroomCapacity(String RoomName, int Capacity, int Used) {
            this.RoomName = RoomName;
            this.Capacity = Capacity;
            this.Used = Used;
        }
    }

    public static class BuildingInfo {
        public String BuildingName;
        public List<ClassroomCapacity> ClassroomList;
        public String EffectiveDateStart;
        public String EffectiveDateEnd;

        BuildingInfo(String BuildingName, List<ClassroomCapacity> ClassroomList,
                     String EffectiveDateStart, String EffectiveDateEnd) {
            this.BuildingName = BuildingName;
            this.ClassroomList = ClassroomList;
            this.EffectiveDateStart = EffectiveDateStart;
            this.EffectiveDateEnd = EffectiveDateEnd;
        }
    }
}
