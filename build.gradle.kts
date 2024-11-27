plugins {
    id("java")
    id("application")
    id("idea")
}

group = "de.tobi1craft.nowbot"
version = findProperty("version")!!

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.dv8tion:JDA:5.2.1")
    implementation("org.mongodb:mongodb-driver-sync:5.2.1")
    implementation("org.apache.logging.log4j:log4j-api:2.24.1")
    runtimeOnly("org.apache.logging.log4j:log4j-core:2.24.1")
    runtimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.24.1")
    implementation("io.github.cdimascio:dotenv-java:3.0.2")
}

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}


configurations.create("myImplementation")
configurations.getByName("myImplementation") {
    extendsFrom(configurations.implementation.get(), configurations.runtimeOnly.get())
    isCanBeResolved = true
}

application {
    mainClass.set("de.tobi1craft.nowbot.NowBot")
}

tasks.compileJava {
    options.release.set(23)
    //options.encoding = "UTF-8"
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "de.tobi1craft.nowbot.NowBot"
    }
    from("LICENSE") {
        rename { "${it}_${project.name}" }
    }
    from({
        configurations["myImplementation"].map { if (it.isDirectory) it else zipTree(it) }
    }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }

}

java {
    sourceCompatibility = JavaVersion.VERSION_23
    targetCompatibility = JavaVersion.VERSION_23
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(23))
    }
}