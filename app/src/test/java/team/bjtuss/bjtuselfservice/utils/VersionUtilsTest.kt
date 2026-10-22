package team.bjtuss.bjtuselfservice.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionUtilsTest {
    @Test
    fun sameVersionWithCommitSuffixIsNotUpdate() {
        assertFalse(VersionUtils.isUpdateAvailable("v1.19.0", "v1.19.0-d103f15"))
    }

    @Test
    fun comparesNumericSegments() {
        assertTrue(VersionUtils.isUpdateAvailable("v1.9.1", "v1.10.0"))
        assertTrue(VersionUtils.isUpdateAvailable("1.19.0", "1.19.1"))
        assertFalse(VersionUtils.isUpdateAvailable("1.19.1", "1.19.0"))
    }

    @Test
    fun ignoresBuildMetadata() {
        assertFalse(VersionUtils.isUpdateAvailable("1.19.0+20", "1.19.0+30"))
    }
}
