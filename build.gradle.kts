plugins {
    id("net.fabricmc.fabric-loom")
}

version = "${sc.properties.get<String>("mod.version")}+${sc.current.version}"
base.archivesName = "regrowth-revamped-fabric"

loom {
    serverOnlyMinecraftJar()

    mods {
        register("regrowth") {
            sourceSet(sourceSets.main.get())
        }
    }

    runConfigs.all {
        runDirectory = rootProject.file("run")
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    implementation("net.fabricmc:fabric-loader:${sc.properties.get<String>("deps.fabric_loader")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${sc.properties.get<String>("deps.fabric_api")}")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks {
    withType<JavaCompile>().configureEach {
        options.release = 25
    }

    test {
        useJUnitPlatform()
    }

    processResources {
        val props =
            mapOf(
                "version" to project.version.toString(),
                "minecraft" to sc.properties.get<String>("mod.mc_compat"),
            )
        inputs.properties(props)
        filesMatching("fabric.mod.json") { expand(props) }
    }

    jar {
        val archivesName = base.archivesName.get()
        inputs.property("archivesName", archivesName)
        from(rootProject.file("LICENSE")) {
            into("META-INF")
            rename { "LICENSE-$archivesName.txt" }
        }
        from(rootProject.file("NOTICE.md")) {
            into("META-INF")
            rename { "NOTICE-$archivesName.txt" }
        }
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds every Minecraft version and copies the jars to build/libs/<mod version>/"
        val modVersion = sc.properties.get<String>("mod.version")
        from(jar.flatMap { it.archiveFile }, named<Jar>("sourcesJar").flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs/$modVersion"))
    }
}
