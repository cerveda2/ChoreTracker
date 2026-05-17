import com.android.build.api.dsl.CommonExtension
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("choretracker.detekt")
            configureAndroidApplication()
        }
    }
}

class AndroidApplicationComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("choretracker.android.application")
            pluginManager.apply("choretracker.android.library.compose")
        }
    }
}

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("choretracker.detekt")
            configureAndroidLibrary()
        }
    }
}

class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            extensions.configure(CommonExtension::class.java) {
                buildFeatures.compose = true
            }
            addComposeDependencies()
        }
    }
}

class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("choretracker.android.library")
            pluginManager.apply("choretracker.android.library.compose")
            dependencies {
                add("implementation", libsCatalog.findLibrary("androidx-core-ktx").get())
                add("implementation", libsCatalog.findLibrary("androidx-lifecycle-runtime-ktx").get())
                add("implementation", libsCatalog.findLibrary("androidx-lifecycle-runtime-compose").get())
                add("implementation", libsCatalog.findLibrary("androidx-lifecycle-viewmodel-ktx").get())
                add("implementation", libsCatalog.findLibrary("androidx-lifecycle-viewmodel-compose").get())
            }
        }
    }
}

class FirebaseConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.withPlugin("com.android.application") {
                pluginManager.apply("com.google.firebase.crashlytics")
                dependencies {
                    add("implementation", platform(libsCatalog.findLibrary("firebase-bom").get()))
                    add("implementation", libsCatalog.findLibrary("firebase-crashlytics").get())
                    add("implementation", libsCatalog.findLibrary("firebase-analytics").get())
                }
            }
        }
    }
}

class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val configure = {
                pluginManager.apply("com.google.devtools.ksp")
                pluginManager.apply("com.google.dagger.hilt.android")
                dependencies {
                    add("implementation", libsCatalog.findLibrary("hilt-android").get())
                    add("ksp", libsCatalog.findLibrary("hilt-compiler").get())
                }
            }
            pluginManager.withPlugin("com.android.application") { configure() }
            pluginManager.withPlugin("com.android.library") { configure() }
        }
    }
}

class RoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            dependencies {
                add("implementation", libsCatalog.findLibrary("room-runtime").get())
                add("implementation", libsCatalog.findLibrary("room-ktx").get())
                add("ksp", libsCatalog.findLibrary("room-compiler").get())
            }
        }
    }
}

class KotlinLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("choretracker.detekt")
            configureJvmLibrary()
            dependencies {
                add("implementation", libsCatalog.findLibrary("kotlinx-coroutines-core").get())
                add("implementation", libsCatalog.findLibrary("kotlinx-serialization-json").get())
                add("implementation", libsCatalog.findLibrary("kotlinx-datetime").get())
                add("testImplementation", libsCatalog.findLibrary("junit4").get())
                add("testImplementation", libsCatalog.findLibrary("google-truth").get())
                add("testImplementation", libsCatalog.findLibrary("mockk").get())
                add("testImplementation", libsCatalog.findLibrary("kotlinx-coroutines-test").get())
                add("testImplementation", libsCatalog.findLibrary("turbine").get())
            }
        }
    }
}

class DetektConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("io.gitlab.arturbosch.detekt")

            extensions.configure<DetektExtension> {
                buildUponDefaultConfig = true
                parallel = true
                ignoreFailures = false
                config.setFrom(rootProject.file("config/detekt/detekt.yml"))
            }

            dependencies {
                add("detektPlugins", libsCatalog.findLibrary("detekt-formatting").get())
            }

            tasks.withType<Detekt>().configureEach {
                jvmTarget = "17"
                basePath = rootDir.absolutePath
                include("**/*.kt")
                exclude("**/resources/**", "**/build/**", "**/generated/**", "**/*.kts")
                reports {
                    html.required.set(true)
                    xml.required.set(true)
                    sarif.required.set(true)
                    md.required.set(false)
                }
            }
        }
    }
}
