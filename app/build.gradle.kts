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
    implementation(libs.kotlinx.coroutines.core)
}

tasks.named<JavaExec>("run") {
    systemProperty("java.library.path", "${project(":frida").projectDir}/src/main/cpp/build")
    dependsOn(":frida:copyNativeLib")
}
