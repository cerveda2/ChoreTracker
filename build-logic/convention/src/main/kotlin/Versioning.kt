import org.gradle.api.Project
import java.util.Properties

data class AppVersion(
    val major: Int,
    val minor: Int,
    val release: Int,
    val build: Int,
) {
    val versionName: String = "$major.$minor.$release"
    val versionCode: Int = (major * 1_000_000) + (minor * 10_000) + (release * 100) + build
}

internal fun Project.loadAppVersion(): AppVersion {
    val properties = Properties().apply {
        rootProject.file("version.properties").inputStream().use(::load)
    }

    return AppVersion(
        major = properties.requireInt("version.major"),
        minor = properties.requireInt("version.minor"),
        release = properties.requireInt("version.release"),
        build = properties.requireInt("version.build"),
    )
}

private fun Properties.requireInt(key: String): Int =
    getProperty(key)?.toIntOrNull() ?: error("Missing or invalid `$key` in version.properties")
