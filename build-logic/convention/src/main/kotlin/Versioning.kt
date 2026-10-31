import org.gradle.api.Project
import java.util.Properties

data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) {
    val versionName: String = "$major.$minor.$patch"
    val versionCode: Int = (major * 1_000_000) + (minor * 10_000) + (patch * 100)
}

internal fun Project.loadAppVersion(): AppVersion {
    val properties = Properties().apply {
        rootProject.file("version.properties").inputStream().use(::load)
    }

    return AppVersion(
        major = properties.requireInt("version.major"),
        minor = properties.requireInt("version.minor"),
        patch = properties.requireInt("version.patch"),
    )
}

private fun Properties.requireInt(key: String): Int =
    getProperty(key)?.toIntOrNull() ?: error("Missing or invalid `$key` in version.properties")
