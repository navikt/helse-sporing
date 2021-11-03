plugins {
    kotlin("jvm") version "1.5.21"
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

val ktorVersion = "1.6.2"
val flywayVersion = "7.13.0"
val hikariVersion = "5.0.0"
val jacksonVersion = "2.12.4"
val kotliqueryVersion = "1.3.1"
val junitJupiterVersion = "5.7.2"
val testcontainersVersion = "1.16.2"

dependencies {
    implementation("com.github.navikt:rapids-and-rivers:2021.07.08-10.12.37eff53b5c39")

    implementation("io.ktor:ktor-jackson:$ktorVersion")
    implementation("org.postgresql:postgresql:42.2.23")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("com.github.seratch:kotliquery:$kotliqueryVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")

    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")

    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    testImplementation("org.testcontainers:testcontainers:$testcontainersVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "16"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "16"
    }

    named<Jar>("jar") {
        archiveFileName.set("app.jar")

        manifest {
            attributes["Main-Class"] = "no.nav.helse.sporing.AppKt"
            attributes["Class-Path"] = configurations.runtimeClasspath.get().joinToString(separator = " ") {
                it.name
            }
        }

        doLast {
            configurations.runtimeClasspath.get().forEach {
                val file = File("$buildDir/libs/${it.name}")
                if (!file.exists())
                    it.copyTo(file)
            }
        }
    }

    withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("skipped", "failed")
        }
    }

    withType<Wrapper> {
        gradleVersion = "7.1.1"
    }
}
