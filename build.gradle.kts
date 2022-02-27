plugins {
    kotlin("jvm") version "1.6.10"
    kotlin("kapt") version "1.6.10"
    kotlin("plugin.serialization") version "1.6.10"
}

group = "it.pureorigins"
version = "1.0.0"

repositories {
    mavenCentral()
    maven(url = "https://nexus.velocitypowered.com/repository/maven-public/")
    maven(url = "https://jitpack.io")
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.0.0")
    compileOnly("com.github.PureOrigins:velocity-language-kotlin:1.0.0")
    compileOnly("com.github.PureOrigins:VelocityConfiguration:1.0.1")
    compileOnly("com.github.PureOrigins:VelocityFriends:1.0.1")
    kapt("com.velocitypowered:velocity-api:3.0.1")
}

kotlin {
    jvmToolchain {
        (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(8))
    }
}