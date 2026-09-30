plugins {
    java
}

group = "com.horrorcraft"
version = "0.1.0"

base {
    archivesName.set("LightsOut")
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

// Match the server exactly: Paper 1.21.6.
val paperApi = "io.papermc.paper:paper-api:1.21.6-R0.1-SNAPSHOT"

dependencies {
    compileOnly(paperApi)
    testImplementation(paperApi)
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
}

val pluginVersion = version.toString()

tasks.processResources {
    inputs.property("version", pluginVersion)
    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Copies the jar into the LightsOut server only (never the Polis server).
val serverPlugins = providers.gradleProperty("serverPlugins").orElse("C:/dev/lightsout-server/plugins")

tasks.register<Copy>("deploy") {
    dependsOn(tasks.jar)
    from(tasks.jar)
    into(serverPlugins)
    rename { "LightsOut.jar" }
}
