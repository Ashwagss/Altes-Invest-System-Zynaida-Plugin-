plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "net.zynaida"
version = "1.0.0"

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
    maven("https://repo.opencollab.dev/main/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("org.geysermc.floodgate:api:2.2.3-SNAPSHOT")
    implementation("com.zaxxer:HikariCP:5.1.0")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.compilerArgs.add("--enable-preview")
    }
    
    shadowJar {
        archiveClassifier.set("")
        archiveFileName.set("EcoMoney-${project.version}.jar")
        
        relocate("com.zaxxer.hikari", "net.zynaida.ecomoney.libs.hikari")
        
        minimize()
    }
    
    build {
        dependsOn(shadowJar)
    }
    
    processResources {
        filesMatching("plugin.yml") {
            expand(
                "version" to project.version,
                "name" to project.name
            )
        }
    }
}
