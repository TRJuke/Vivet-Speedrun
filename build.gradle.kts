plugins {
    id("fabric-loom") version "1.17.21"
}

fun prop(name: String): String = project.property(name) as String

val flavour = (findProperty("flavour") as String?) ?: "site"
require(flavour == "site" || flavour == "modrinth")

version = prop("mod_version") + (if (flavour == "modrinth") "+m" else "")
group = prop("maven_group")

base {
    archivesName.set(prop("archives_base_name"))
}

sourceSets.named("main") {
    java.srcDir("src/$flavour/java")
}

repositories {
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
    mavenCentral()
}

dependencies {
    "minecraft"("com.mojang:minecraft:" + prop("minecraft_version"))
    "implementation"("net.fabricmc:fabric-loader:" + prop("loader_version"))
    "implementation"("net.fabricmc.fabric-api:fabric-api:" + prop("fabric_version"))

    "testImplementation"("net.fabricmc:fabric-loader-junit:" + prop("loader_version"))
    "testImplementation"("org.junit.jupiter:junit-jupiter:5.12.2")
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher:1.12.2")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    inputs.property("version", version)
    inputs.property("minecraft_version", prop("minecraft_version"))
    inputs.property("loader_version", prop("loader_version"))

    filesMatching("fabric.mod.json") {
        expand(
            "version" to version,
            "minecraft_version" to prop("minecraft_version"),
            "loader_version" to prop("loader_version"),
        )
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

val publicJar by tasks.registering {
    group = "build"
    dependsOn(tasks.test, "assemble")
}
