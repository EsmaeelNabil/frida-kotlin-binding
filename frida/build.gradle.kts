import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.0.0"
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
}

tasks.test {
    useJUnitPlatform()
}


tasks.register<Exec>("generateSwigInterface") {
    workingDir = file("src/main/cpp")
    commandLine("bash", "./generate.sh")
}

tasks.register<Exec>("generateSwig") {
//    dependsOn("generateSwigInterface")
    workingDir = file("src/main/cpp")

    doFirst {
        file("src/main/cpp/generated").mkdirs()
        file("src/main/java/dev/supersam/fridaSource").mkdirs()
    }

    commandLine(
        "/opt/homebrew/bin/swig",
        "-java",
        "-c++",
        "-I/opt/homebrew/include/glib-2.0",
        "-I/opt/homebrew/lib/glib-2.0/include",
        "-I/opt/homebrew/include",
        "-I/usr/include",
        "-package", "dev.supersam.fridaSource",
        "-outdir", "../java/dev/supersam/fridaSource",
        "-o", "generated/frida_wrap.cpp",
        "Frida.i"
    )
}

tasks.register<Exec>("buildNative") {
    dependsOn("generateSwig")
    workingDir = file("src/main/cpp")
    commandLine("bash", "-c", "cmake -B build . && cmake --build build")
}

// Copy the native library to multiple locations to ensure it's found
tasks.register<Copy>("copyNativeLib") {
    from("${projectDir}/src/main/cpp/build/")
    into("${projectDir}/src/main/resources/native/${osDirectory()}")  // OS-specific directory
    include("*.dylib")
    dependsOn("buildNative")
}

// Helper function to determine OS-specific directory
fun osDirectory(): String {
    val os = System.getProperty("os.name").toLowerCase()
    return when {
        os.contains("mac") -> "macos"
        os.contains("linux") -> "linux"
        os.contains("windows") -> "windows"
        else -> throw GradleException("Unsupported operating system: $os")
    }
}

// Add source sets to include generated Java files
sourceSets {
    main {
        java {
            srcDir("src/main/java")
        }
        resources {
            srcDir("src/main/resources")
        }
    }
}


tasks.withType<JavaCompile> {
    dependsOn("generateSwig")
}

tasks.withType<KotlinCompile> {
    dependsOn("generateSwig")
}

// Ensure resources are processed after native library is built
tasks.named("processResources") {
    dependsOn("copyNativeLib")
}

tasks.register<Delete>("cleanGenerated") {
    doLast {
        println("Cleaning generated files...")
        file("src/main/cpp/generated").deleteRecursively()
        file("src/main/java/dev/supersam/fridaSource").deleteRecursively()
        println("Deleted generated cpp directory")
    }
}

tasks.named("clean") {
    dependsOn("cleanGenerated")
}

tasks.processResources {
    from("src/main/resources") {
        include("native*.dylib")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}