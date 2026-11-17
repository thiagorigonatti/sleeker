/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

plugins {
    id("java")
    id("com.gradleup.shadow") version "9.4.2"
    id("com.google.osdetector") version "1.7.3"
    id("java-library")
}

group = "me.thiagorigonatti"
version = "0.0.25"
description =
    "A high-throughput sub-millisecond p99 latency ultra high-performance reactive non-blocking I/O io_uring and unix-domain-sockets compatible HTTP1/2/3 server module with easy TLS integration support."
repositories {
    mavenCentral()
}

val os = the<com.google.gradle.osdetector.OsDetector>()

dependencies {

    api("io.netty:netty-codec-http2:4.2.20.Final")

    runtimeOnly("io.netty:netty-tcnative-boringssl-static:2.0.84.Final:${os.classifier}")

    implementation("io.netty:netty-transport-native-io_uring:4.2.20.Final:linux-x86_64")
    implementation("io.netty:netty-transport-native-epoll:4.2.20.Final:linux-x86_64")
    implementation("io.netty:netty-transport-native-kqueue:4.2.20.Final:osx-x86_64")

    api("org.apache.logging.log4j:log4j-api:2.26.1")
    runtimeOnly("org.apache.logging.log4j:log4j-core:2.26.1")

    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.23.0")
    implementation("tools.jackson.core:jackson-databind:3.3.0")

}

tasks.shadowJar {
    archiveClassifier.set("all")
    archiveFileName.set("${archiveBaseName.get()}-${version}-${archiveClassifier.get()}-${os.classifier}.jar")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
