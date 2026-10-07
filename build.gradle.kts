plugins {
    java
    id("com.gradleup.shadow") version "9.1.0"
}

group = "com.valaryrpg"
version = "1.0.1"
description = "Modular Paper 1.21.11 core plugin"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("me.clip:placeholderapi:2.11.6")

    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("org.xerial:sqlite-jdbc:3.47.2.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.3")

    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    processResources {
        val props = mapOf(
            "version" to version,
            "description" to project.description
        )
        filesMatching(listOf("plugin.yml", "paper-plugin.yml")) {
            expand(props)
        }
        filteringCharset = "UTF-8"
    }

    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
        options.compilerArgs.add("-Xlint:deprecation")
    }

    shadowJar {
        archiveClassifier.set("")
        archiveBaseName.set("ValaryRPG-Core")
        relocate("com.zaxxer.hikari", "com.valaryrpg.core.libs.hikari")
        mergeServiceFiles()
        minimize {
            exclude(dependency("org.xerial:sqlite-jdbc:.*"))
            exclude(dependency("org.mariadb.jdbc:mariadb-java-client:.*"))
        }
        exclude("org/sqlite/native/Linux-Android/**")
        exclude("org/sqlite/native/Linux-Musl/**")
        exclude("org/sqlite/native/Linux-Alpine/**")
        exclude("org/sqlite/native/FreeBSD/**")
        exclude("org/sqlite/native/Windows/x86/**")
        exclude("org/sqlite/native/Linux/x86/**")
        exclude("org/sqlite/native/Linux/arm/**")
        exclude("org/sqlite/native/Linux/armv6/**")
        exclude("org/sqlite/native/Linux/armv7/**")
        exclude("org/sqlite/native/Linux/android-arm/**")
        exclude("org/sqlite/native/Linux/ppc64/**")
        exclude("org/sqlite/native/Linux/riscv64/**")
        exclude("org/sqlite/native/Mac/x86/**")
    }

    jar {
        enabled = false
    }

    assemble {
        dependsOn(shadowJar)
    }

    test {
        useJUnitPlatform()
    }
}
