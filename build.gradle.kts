plugins {
    id("java")
    id("net.neoforged.moddev") version "2.0.141"
}

group = "com.gameplayadditions"
version = project.property("mod_version").toString()

base {
    archivesName.set(project.property("archives_base_name").toString())
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
}

neoForge {
    version = project.property("neoforge_version").toString()

    runs {
        configureEach {
            systemProperty("neoforge.logging.console.level", "debug")
        }

        create("client") {
            client()
        }

        create("server") {
            server()
        }

        create("data") {
            data()
            programArguments.addAll("--mod", project.property("mod_id").toString(), "--all", "--output", file("src/generated/resources/").absolutePath)
        }
    }

    mods {
        create(project.property("mod_id").toString()) {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    // SQLite (from MC-Plugin)
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")

    // SnakeYAML — for config.yml / messages.yml parsing
    implementation("org.yaml:snakeyaml:2.3")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}
