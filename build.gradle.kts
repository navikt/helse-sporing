plugins {
    kotlin("jvm") version "1.9.10"
}

repositories {
    mavenCentral()
}
tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "17"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "17"
    }

    withType<Wrapper> {
        gradleVersion = "8.3"
    }
}

allprojects {
    tasks {
        withType<Test> {
            useJUnitPlatform()
            testLogging {
                events("passed", "skipped", "failed")
            }
        }
    }
}
