plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

group = "dev.supersam"


repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "dev.supersam.app.AppKt"
}


dependencies {
    implementation(libs.guava)
    implementation(projects.frida)
}

tasks.named<JavaExec>("run") {
    // Set the path to where the native library is located
    systemProperty("java.library.path", "${project(":frida").projectDir}/src/main/cpp/build")

    // Ensure the native library is built and copied before running
    dependsOn(":frida:copyNativeLib")
}