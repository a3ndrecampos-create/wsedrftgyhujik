plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("io.ktor.plugin") version "3.0.1"
}

group = "com.promoserver"
version = "0.1.0"

application {
    mainClass.set("com.promoserver.ApplicationKt")
    // JVM enxuta para planos de hospedagem pequenos. Sobrescreva com JAVA_OPTS se tiver mais memória.
    applicationDefaultJvmArgs = listOf("-Xmx300m", "-XX:+UseSerialGC", "-Dfile.encoding=UTF-8")
}

kotlin {
    jvmToolchain(17)
}

ktor {
    fatJar {
        archiveFileName.set("promo-server.jar")
    }
}

repositories {
    mavenCentral()
}

val ktorVersion = "3.0.1"
val exposedVersion = "0.56.0"

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-cio-jvm:$ktorVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")

    implementation("org.jsoup:jsoup:1.18.1")

    implementation("ch.qos.logback:logback-classic:1.5.12")

    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion")
    testImplementation("io.ktor:ktor-client-mock-jvm:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

tasks.test {
    useJUnitPlatform()
}

// ZIP pronto para enviar à ShardCloud: JAR executável + .shardcloud + promo.env + sites.json.
// Se promo.env ou sites.json não existirem na raiz do projeto, usa os modelos de exemplo.
tasks.register<Zip>("shardcloudZip") {
    group = "distribution"
    description = "Gera build/distributions/promo-server-shardcloud.zip para enviar à ShardCloud."
    dependsOn("buildFatJar")
    archiveFileName.set("promo-server-shardcloud.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(layout.buildDirectory.file("libs/promo-server.jar"))
    from(file("deploy/shardcloud/.shardcloud"))
    from(file("promo.env").takeIf { it.exists() } ?: file("deploy/shardcloud/promo.env.example")) { rename { "promo.env" } }
    from(file("sites.json").takeIf { it.exists() } ?: file("sites.example.json")) { rename { "sites.json" } }
}
