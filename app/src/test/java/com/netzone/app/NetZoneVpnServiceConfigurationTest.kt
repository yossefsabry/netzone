package com.netzone.app

import java.nio.file.Files
import java.nio.file.Path
import kotlin.text.Charsets.UTF_8
import org.junit.Assert.assertTrue
import org.junit.Test

class NetZoneVpnServiceConfigurationTest {

    @Test
    fun vpnServiceDeclaresSpecialUseForegroundTypeInManifest() {
        val manifest = readProjectFile("src", "main", "AndroidManifest.xml")

        assertTrue(
            "VPN service should declare android:foregroundServiceType=specialUse",
            manifest.contains("android:name=\".NetZoneVpnService\"") &&
                manifest.contains("android:foregroundServiceType=\"specialUse\"")
        )
    }

    @Test
    fun vpnServiceUsesExplicitSpecialUseForegroundTypeAtRuntime() {
        val source = readProjectFile("src", "main", "java", "com", "netzone", "app", "NetZoneVpnService.kt")

        assertTrue(
            "VPN service should pass FOREGROUND_SERVICE_TYPE_SPECIAL_USE when starting in foreground",
            source.contains("ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE")
        )
    }

    private fun readProjectFile(vararg pathSegments: String): String {
        val userDir = Path.of(System.getProperty("user.dir"))
        val candidates = listOf(
            resolve(userDir, pathSegments.asList()),
            resolve(userDir.resolve("app"), pathSegments.asList())
        )

        val file = candidates.firstOrNull(Files::exists)
            ?: error("Could not locate ${pathSegments.joinToString("/")}")

        return String(Files.readAllBytes(file), UTF_8)
    }

    private fun resolve(base: Path, segments: List<String>): Path {
        return segments.fold(base) { path, segment ->
            path.resolve(segment)
        }
    }
}
