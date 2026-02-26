plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

group = "dev.supersam"
version = "0.0.1"

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
