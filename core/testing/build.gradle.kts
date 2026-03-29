plugins {
    id("choretracker.kotlin.library")
}

dependencies {
    implementation(project(":core:data-contract"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))
}
