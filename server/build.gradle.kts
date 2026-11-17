/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

plugins {
    id("application")
    id("com.gradleup.shadow") version "9.4.2"
}

group = "me.thiagorigonatti"
version = "0.0.25"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    dependencies {
        implementation(project(":"))
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveFileName.set("app.jar")

    manifest {
        attributes["Main-Class"] = "me.thiagorigonatti.sleeker.server.Application"
    }
}
