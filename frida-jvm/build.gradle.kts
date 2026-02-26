plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

group = "dev.supersam"
version = "0.0.1"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    testImplementation(kotlin("test"))
    api(libs.kotlinx.coroutines.core)
    api(project(":frida-api"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<Exec>("downloadFridaDevkit") {
    workingDir = file("src/main/cpp")
    commandLine("bash", "download_frida_devkit.sh")
}

tasks.register<Exec>("buildNative") {
    dependsOn("downloadFridaDevkit")
    workingDir = file("src/main/cpp")
    commandLine("bash", "-c", "cmake -B build . && cmake --build build")
}

tasks.register<Copy>("copyNativeLib") {
    from("${projectDir}/src/main/cpp/build/")
    into("${projectDir}/src/main/resources/native/${osDirectory()}")
    include("*.dylib", "*.so")
    dependsOn("buildNative")
}

fun osDirectory(): String {
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.contains("mac") -> "macos"
        os.contains("linux") -> "linux"
        else -> throw GradleException("Unsupported operating system: $os")
    }
}

sourceSets {
    main {
        resources {
            srcDir("src/main/resources")
        }
    }
}

tasks.named("processResources") {
    dependsOn("copyNativeLib")
}

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
