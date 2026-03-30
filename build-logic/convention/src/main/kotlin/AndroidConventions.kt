import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion

internal val Project.libsCatalog: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

private fun ApplicationExtension.configureCommon() {
    compileSdk = SdkVersion.COMPILE_SDK

    defaultConfig {
        minSdk = SdkVersion.MIN_SDK
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

private fun LibraryExtension.configureCommon() {
    compileSdk = SdkVersion.COMPILE_SDK

    defaultConfig {
        minSdk = SdkVersion.MIN_SDK
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

private fun Project.configureKotlinCompiler() {
    tasks.withType(KotlinCompile::class.java).configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.add("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
            freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
        }
    }
}

internal fun Project.configureAndroidLibrary() {
    extensions.configure<LibraryExtension> {
        configureCommon()
    }
    configureKotlinCompiler()
}

internal fun Project.configureAndroidApplication() {
    val appVersion = loadAppVersion()
    extensions.configure<ApplicationExtension> {
        configureCommon()
        defaultConfig {
            targetSdk = SdkVersion.TARGET_SDK
            versionCode = appVersion.versionCode
            versionName = appVersion.versionName
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }
    configureKotlinCompiler()
}

internal fun Project.addComposeDependencies() {
    dependencies {
        add("implementation", platform(libsCatalog.findLibrary("androidx-compose-bom").get()))
        add("androidTestImplementation", platform(libsCatalog.findLibrary("androidx-compose-bom").get()))
        add("implementation", libsCatalog.findLibrary("androidx-compose-ui").get())
        add("implementation", libsCatalog.findLibrary("androidx-compose-ui-graphics").get())
        add("implementation", libsCatalog.findLibrary("androidx-compose-ui-tooling-preview").get())
        add("implementation", libsCatalog.findLibrary("androidx-compose-foundation").get())
        add("implementation", libsCatalog.findLibrary("androidx-compose-material3").get())
        add("implementation", libsCatalog.findLibrary("androidx-compose-material-icons-extended").get())
        add("debugImplementation", libsCatalog.findLibrary("androidx-compose-ui-tooling").get())
        add("debugImplementation", libsCatalog.findLibrary("androidx-compose-ui-test-manifest").get())
        add("androidTestImplementation", libsCatalog.findLibrary("androidx-compose-ui-test-junit4").get())
    }
}

internal fun Project.configureJvmLibrary() {
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    }
    configureKotlinCompiler()
}
