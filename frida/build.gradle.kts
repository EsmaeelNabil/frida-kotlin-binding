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
    implementation("com.github.jnr:jnr-ffi:2.2.17")
}

tasks.test {
    useJUnitPlatform()
}


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

tasks.processResources {
    from("src/main/resources")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}