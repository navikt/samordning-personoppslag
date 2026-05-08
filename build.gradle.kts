import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

val kotlinVersion = "2.3.20"
val prometeusVersion = "1.16.5"
val springbootVersion = "4.0.6"
val springkafkaVersion="4.0.5"
val springframeworkbomVersion = "7.0.7"
val slf4jVersion = "2.0.17"
val logstashlogbackVersion = "9.0"
val tokensupportVersion = "6.0.6"
val mockOAuth2ServerVersion = "3.0.3"
val jakartaAnnotationApiVersion = "3.0.0"
val jakartaInjectApiVersion = "2.0.1"
val mockkVersion = "1.14.9"
val springmockkVersion = "5.0.1"
val junitplatformVersion = "6.0.3"
val commonsLang3Version = "3.20.0"

plugins {
    val pluginSpringBootVersion = "4.0.4"
    val pluginKotlinVersion = "2.3.20"

    kotlin("jvm") version pluginKotlinVersion
    kotlin("plugin.spring") version pluginKotlinVersion
    kotlin("plugin.jpa") version pluginKotlinVersion
    id("org.springframework.boot") version pluginSpringBootVersion
    id("io.spring.dependency-management") version "1.1.7"
    id("org.owasp.dependencycheck") version "12.1.9"
}

group = "no.nav.pensjon"
java.sourceCompatibility = JavaVersion.VERSION_25

repositories {
    mavenCentral()


    maven {
        url = uri("https://packages.confluent.io/maven")
    }

    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }

    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
        metadataSources {
            artifact() //Look directly for artifact
        }
    }

}

dependencies {

    // Spring Boot & Framework
    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springbootVersion"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-aspectj")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-actuator")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation(platform("org.springframework:spring-framework-bom:$springframeworkbomVersion"))
    implementation("org.springframework.retry:spring-retry:2.0.12")

    //caffeine cache manager
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.3")

    implementation("org.springframework.boot:spring-boot-starter-kafka")
    testImplementation("org.springframework.kafka:spring-kafka-test:$springkafkaVersion")
    implementation("io.confluent:kafka-avro-serializer:8.1.1") {
        exclude(group = "org.apache.avro", module = "avro")
    }
    implementation("no.nav.pensjon:pensjon-pdl-avro-schema:2025.08.14-08.26-800400e1dc81")
    implementation("org.apache.avro:avro:1.12.1")

    //spring boot 3.0 jakaera-api
    implementation("jakarta.annotation:jakarta.annotation-api:$jakartaAnnotationApiVersion")
    implementation("jakarta.inject:jakarta.inject-api:$jakartaInjectApiVersion")

    // Kotlin + Jackson 3 (tools.jackson group = Jackson 3 artifacts, JavaTimeModule now built into jackson-databind)
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")

    // Token support Azuread, Oidc
    implementation("no.nav.security:token-validation-spring:$tokensupportVersion")
    implementation("no.nav.security:token-validation-jaxrs:$tokensupportVersion")
    implementation("no.nav.security:token-client-spring:$tokensupportVersion")

    testImplementation("no.nav.security:mock-oauth2-server:$mockOAuth2ServerVersion")
    testImplementation("no.nav.security:token-validation-spring-test:$tokensupportVersion")

    // Logging
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashlogbackVersion")
    implementation("org.slf4j:jcl-over-slf4j:$slf4jVersion")
    // Micrometer
    implementation("io.micrometer:micrometer-registry-prometheus:$prometeusVersion")

    // div
    implementation("org.apache.commons:commons-lang3:$commonsLang3Version")

    // test
    testImplementation("com.ninja-squad:springmockk:$springmockkVersion")

    // mock - test
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.junit.platform:junit-platform-suite-api:$junitplatformVersion")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
}

tasks {
    withType<Test> {
        useJUnitPlatform()
        failFast = true
        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }


    withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_25)
            freeCompilerArgs.add("-Xjsr305=strict")
        }
    }

    withType<Jar> {
        archiveBaseName.set("samordning-personoppslag")
    }

    withType<Wrapper> {
        gradleVersion = "9.4.0"
    }

    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.apache.commons" && requested.module.toString() == "commons-lang") {
                useVersion(commonsLang3Version)
            }
        }
    }
}