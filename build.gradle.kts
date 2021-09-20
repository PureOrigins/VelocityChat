plugins {
    kotlin("jvm")
    kotlin("kapt")
    kotlin("plugin.serialization")
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
    compileOnly("com.github.PureOrigins:VelocityFriends:1.0.0")
    kapt("com.velocitypowered:velocity-api:3.0.0")
}
