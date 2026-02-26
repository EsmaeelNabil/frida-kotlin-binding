plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

group = "dev.supersam"
version = "0.0.1"

repositories {
    mavenCentral()
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    val iosTargets = listOf(iosArm64(), iosSimulatorArm64())

    iosTargets.forEach { target ->
        target.compilations.getByName("main") {
            val fridaCore by cinterops.creating {
                defFile(project.file("src/nativeInterop/cinterop/frida-core.def"))
                packageName("frida_core")
                includeDirs(project.file("src/native"))
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":frida-api"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

tasks.register<Exec>("downloadFridaDevkitIos") {
    // Create directory if not exists
    doFirst {
        file("src/native").mkdirs()
    }
    workingDir = file("src/native")
    // Use the script from frida-jvm
    // We assume the script is modified to accept an argument for OS
    commandLine("bash", "${rootProject.projectDir}/frida-jvm/src/main/cpp/download_frida_devkit.sh", "ios")
}

// Make sure cinterop tasks depend on downloading the devkit
tasks.withType<org.jetbrains.kotlin.gradle.tasks.CInteropProcess> {
    dependsOn("downloadFridaDevkitIos")
}
