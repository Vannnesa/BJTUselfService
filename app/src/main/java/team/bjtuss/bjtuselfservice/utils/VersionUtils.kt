package team.bjtuss.bjtuselfservice.utils

/**
 * Compares only the numeric SemVer portion of a release tag.
 * Build suffixes such as v1.19.0-d103f15 do not make the same version newer.
 */
object VersionUtils {
    fun isUpdateAvailable(current: String, latest: String): Boolean {
        if (latest.isBlank()) return false
        return compare(current, latest) < 0
    }

    fun compare(current: String, latest: String): Int {
        val left = numericParts(current)
        val right = numericParts(latest)
        val size = maxOf(left.size, right.size)
        for (index in 0 until size) {
            val a = left.getOrElse(index) { 0 }
            val b = right.getOrElse(index) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }

    private fun numericParts(version: String): List<Int> =
        version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
            .split('.')
            .map { it.toIntOrNull() ?: 0 }
}
