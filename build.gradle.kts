import com.google.protobuf.gradle.id
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.protobuf)
}

group = "io.github.nekzabirov"
version = "0.0.1-SNAPSHOT"

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Plain SQL over JdbcClient; the schema is Flyway's and only the `schema` launcher applies it.
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    // The starter, not flyway-core alone: Boot 4 moved the auto-configuration out of spring-boot-autoconfigure,
    // and without it spring.flyway.* is inert and nothing ever migrates.
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    implementation("org.springframework.boot:spring-boot-starter-amqp")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")

    implementation("org.springframework.security:spring-security-crypto")
    implementation("org.apache.httpcomponents.client5:httpclient5")

    // The Currency-Exchange-Service contract, compiled from src/main/proto (see the protobuf block below).
    implementation("io.grpc:grpc-protobuf")
    implementation("io.grpc:grpc-stub")
    implementation("com.google.protobuf:protobuf-java")
    implementation("io.grpc:grpc-netty-shaded")

    implementation(libs.logstash.encoder)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-rabbitmq")
    // The currency service in-process: the real client talks to a fake service at fixed rates, never to the network.
    testImplementation("io.grpc:grpc-inprocess")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// protoc and the gRPC generator at the versions the Boot BOM gives protobuf-java and grpc-java, so the generated
// code always matches the runtime it is compiled against.
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${dependencyManagement.importedProperties.getValue("protobuf-java.version")}"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${dependencyManagement.importedProperties.getValue("grpc-java.version")}"
        }
    }
    generateProtoTasks {
        all().configureEach {
            // maybeCreate, not id(): the action can reach the same task more than once.
            plugins {
                maybeCreate("grpc")
            }
        }
    }
}

// One fat jar under a fixed name: the Dockerfile copies exactly this file, and a version in the name would
// break the image job on the first bump.
tasks.bootJar {
    archiveFileName.set("affiliate-app.jar")
}

// The plain jar would sit beside the boot jar in build/libs and be picked up by a wildcard COPY.
tasks.jar {
    enabled = false
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = TestExceptionFormat.FULL
    }
}
