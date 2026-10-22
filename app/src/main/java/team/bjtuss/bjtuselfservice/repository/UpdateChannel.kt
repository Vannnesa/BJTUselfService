package team.bjtuss.bjtuselfservice.repository

enum class UpdateChannel(
    val key: String,
    val label: String,
    val latestReleaseUrl: String,
) {
    VANNESA(
        key = "vannesa",
        label = "Vannesa 版",
        latestReleaseUrl = "https://api.github.com/repos/Vannnesa/BJTUselfService/releases/latest",
    ),
    ORIGINAL(
        key = "original",
        label = "原版",
        latestReleaseUrl = "https://api.github.com/repos/HFDLYS/BJTUselfService/releases/latest",
    );

    companion object {
        fun from(value: String?): UpdateChannel =
            entries.firstOrNull { it.key.equals(value, ignoreCase = true) } ?: VANNESA
    }
}
