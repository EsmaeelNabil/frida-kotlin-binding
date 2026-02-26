plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.supersam.frida.gadget"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
}

tasks.register<Exec>("downloadGadget") {
    workingDir = file(".")
    commandLine("bash", "download_gadget.sh")
}

afterEvaluate {
    tasks.named("preBuild") {
        dependsOn("downloadGadget")
    }
}
