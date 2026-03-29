import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
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
            configureJvmLibrary()
            dependencies {
                add("implementation", libsCatalog.findLibrary("kotlinx-coroutines-core").get())
                add("implementation", libsCatalog.findLibrary("kotlinx-serialization-json").get())
                add("implementation", libsCatalog.findLibrary("kotlinx-datetime").get())
                add("testImplementation", libsCatalog.findLibrary("junit4").get())
                add("testImplementation", libsCatalog.findLibrary("google-truth").get())
                add("testImplementation", libsCatalog.findLibrary("kotlinx-coroutines-test").get())
                add("testImplementation", libsCatalog.findLibrary("turbine").get())
            }
        }
    }
}
