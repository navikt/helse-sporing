plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

val ktorVersion = "1.6.7"
val flywayVersion = "8.4.1"
val hikariVersion = "5.0.1"
val jacksonVersion = "2.13.2"
val kotliqueryVersion = "1.6.0"
val junitJupiterVersion = "5.8.2"
val testcontainersVersion = "1.16.2"
val rapidsAndRiversVersion = "rapids-and-rivers:2022.03.30-21.22.dbaa41649bd3"

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.github.navikt:$rapidsAndRiversVersion")

    implementation("io.ktor:ktor-jackson:$ktorVersion")
    implementation("org.postgresql:postgresql:42.3.1")
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
}
