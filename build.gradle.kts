plugins {
    java
}

group = "com.chestclaims"
version = "1.0.20"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly(files("../../Bits/build/Bops-1.1.2.jar"))
}

tasks.jar {
    archiveBaseName.set("ChestClaims")
    destinationDirectory.set(file("build"))
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}
