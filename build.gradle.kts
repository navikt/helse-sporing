plugins {
    alias(libs.plugins.sykepenger.deployable)
}

sykepengerDeployable {
    mainClass = "no.nav.helse.sporing.AppKt"
}

dependencies {
    implementation(libs.rapidsAndRivers)
    implementation(libs.tbdLibs.naisfulApp)
    implementation(libs.tbdLibs.azureTokenClientDefault)

    implementation(libs.postgresql)
    implementation(libs.hikariCP)
    implementation(libs.kotliquery)
    implementation(libs.flyway.postgresql)

    testImplementation(libs.tbdLibs.rapidsAndRiversTest)
    testImplementation(libs.tbdLibs.postgresTestdatabaser)
}
