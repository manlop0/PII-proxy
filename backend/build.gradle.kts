import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.logging.TestLogEvent.*

plugins {
  java
  application
  id("com.gradleup.shadow") version "9.2.2"
}

group = "com.project.piiproxy"
version = "1.0.0-SNAPSHOT"

repositories {
  mavenCentral()
}

val vertxVersion = "5.0.10"
val junitJupiterVersion = "5.9.1"

val mainVerticleName = "com.project.piiproxy.MainVerticle"
val launcherClassName = "io.vertx.launcher.application.VertxApplication"

application {
  mainClass.set(launcherClassName)
}

dependencies {
  implementation(platform("io.vertx:vertx-stack-depchain:$vertxVersion"))
  implementation("io.vertx:vertx-launcher-application")
  implementation("io.vertx:vertx-web")
  implementation("io.vertx:vertx-config")
  implementation("io.vertx:vertx-config-yaml")
  implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
  implementation("org.mapdb:mapdb:3.0.9")
  implementation("com.google.re2j:re2j:1.8")
  implementation("ch.qos.logback:logback-classic:1.4.14")
  implementation("com.microsoft.onnxruntime:onnxruntime:1.25.1")
  implementation("ai.djl.huggingface:tokenizers:0.36.0")
  testImplementation("io.vertx:vertx-junit5")
  testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
  sourceCompatibility = JavaVersion.VERSION_21
  targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<ShadowJar> {
  archiveClassifier.set("fat")
  manifest {
    attributes(mapOf("Main-Verticle" to mainVerticleName))
  }
  mergeServiceFiles()
}

tasks.withType<Test> {
  useJUnitPlatform()
  testLogging {
    events = setOf(PASSED, SKIPPED, FAILED)
  }
}

tasks.withType<JavaExec> {
  args = listOf(mainVerticleName)
}
