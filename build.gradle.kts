plugins {
    id("java")
    id("com.gradleup.shadow") version "9.4.2"
    id("com.google.osdetector") version "1.7.3"
}

group = "me.thiagorigonatti"
version = "0.0.15"
description =
    "A high-throughput sub-millisecond p99 latency ultra high-performance reactive non-blocking I/O io_uring and unix-domain-sockets compatible HTTP1/2/3 server module with easy TLS integration support."
repositories {
    mavenCentral()
}

val os = the<com.google.gradle.osdetector.OsDetector>()

dependencies {
    implementation("io.netty:netty-all:4.2.18.Final")
    implementation("io.netty:netty-tcnative-boringssl-static:2.0.84.Final:${os.classifier}")

    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.22.3")
    implementation("org.apache.logging.log4j:log4j-api:2.26.1")
    implementation("org.apache.logging.log4j:log4j-core:2.26.1")

    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.86")
    implementation("org.bouncycastle:bctls-jdk18on:1.86.1")

    implementation("tools.jackson.core:jackson-databind:3.2.3")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    implementation("jakarta.validation:jakarta.validation-api:3.1.1")

    implementation("org.postgresql:r2dbc-postgresql:1.1.3.RELEASE")
    implementation("io.r2dbc:r2dbc-spi:1.0.0.RELEASE")
    implementation("io.r2dbc:r2dbc-pool:1.0.2.RELEASE")


}

tasks.register<Exec>("runSleekerTestNamed") {
    group = "application"
    description = "Roda a classe de teste com nome de processo customizado (Linux only)"

    dependsOn(tasks.testClasses)

    commandLine = listOf(
        "bash", "-c",
        "exec -a SleekerTest java -Dio.netty.allocator.type=pooled -Dio.netty.allocator.maxOrder=1 -Xms16m -Xmx32m -XX:MaxMetaspaceSize=32m -XX:CompressedClassSpaceSize=4m -XX:+UseSerialGC -XX:-UseStringDeduplication -Xss256k -XX:+ExitOnOutOfMemoryError -noverify -Djava.awt.headless=true -XX:+UseCompressedOops -cp ${sourceSets["test"].runtimeClasspath.asPath} me.thiagorigonatti.sleeker.SleekerServerTest"
    )
}

tasks.shadowJar {
    exclude("me/thiagorigonatti/sleeker/aaa_dev_test/**")

    archiveClassifier.set("all")
    archiveFileName.set("${archiveBaseName.get()}-${version}-${archiveClassifier.get()}-${os.classifier}.jar")
}

tasks.withType<Jar> {
    manifest.attributes["Main-Class"] = "me.thiagorigonatti.sleeker.aaa_dev_test.Test"
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
