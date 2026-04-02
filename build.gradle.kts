import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.util.VersionNumber
import java.util.Properties

plugins {
  id("java-gradle-plugin")
  id("groovy")
  id("maven-publish")
  id("com.gradle.plugin-publish") version "0.12.0"
  id("org.scm-manager.license") version "0.8.0"
}

repositories {
  mavenCentral()
  maven {
    url = uri("https://plugins.gradle.org/m2/")
  }
}

val jettyVersion = "11.0.16"

dependencies {
  implementation(gradleApi())
  implementation("com.github.node-gradle:gradle-node-plugin:2.2.4")
  implementation("org.eclipse.jetty:jetty-server:$jettyVersion")
  implementation("org.eclipse.jetty:jetty-webapp:$jettyVersion")
  implementation("com.sun.xml.bind:jaxb-osgi:2.2.4-1")
  implementation("com.google.guava:guava:32.0.1-jre")
  implementation("org.codehaus.groovy:groovy-yaml:3.0.19")
  implementation("io.swagger.core.v3:swagger-gradle-plugin:2.2.19")
  implementation("org.scm-manager:gradle-license-plugin:0.8.0")
  implementation("org.sonarsource.scanner.gradle:sonarqube-gradle-plugin:3.0")
  implementation("org.scm-manager.changelog:gradle-plugin:0.2.0")

  testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.0")
  testImplementation("org.junit.jupiter:junit-jupiter-params:5.7.0")
  testImplementation("org.junit.jupiter:junit-jupiter-engine:5.7.0")
  testImplementation("org.assertj:assertj-core:3.11.1")
}

tasks.withType<GroovyCompile>().configureEach {
  sourceCompatibility = JavaVersion.VERSION_17.toString()
  targetCompatibility = JavaVersion.VERSION_17.toString()
}

tasks.withType<JavaCompile>().configureEach {
  sourceCompatibility = JavaVersion.VERSION_17.toString()
  targetCompatibility = JavaVersion.VERSION_17.toString()
}

tasks.test {
  useJUnitPlatform()
}

gradlePlugin {
  plugins {
    create("smpPlugin") {
      id = "org.scm-manager.smp"
      implementationClass = "com.cloudogu.smp.GradleSmpPlugin"
    }
  }
}

val functionalTest by sourceSets.creating
gradlePlugin.testSourceSets(functionalTest)
configurations[functionalTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())

val functionalTestTask = tasks.register<Test>("functionalTest") {
  testClassesDirs = functionalTest.output.classesDirs
  classpath = functionalTest.runtimeClasspath
  useJUnitPlatform()
}

tasks.named("check") {
  dependsOn(functionalTestTask)
  dependsOn("checkLicenses")
}

pluginBundle {
  website = "https://scm-manager.org"
  vcsUrl = "https://github.com/scm-manager/gradle-smp-plugin"
  description = "Plugin to build and test SCM-Manager plugins"
  tags = listOf("scm-manager", "smp", "plugin")

  plugins {
    named("smpPlugin") {
      displayName = "Gradle SCM-Manager Plugin"
    }
  }
}

tasks.register("setVersion") {
  doLast {
    if (!project.hasProperty("newVersion")) {
      throw GradleException("usage setVersion -PnewVersion=x.y.z")
    }

    val newVersion = project.property("newVersion").toString()
    setVersion(newVersion)
  }
}

tasks.register("setVersionToNextSnapshot") {
  doLast {
    val v = VersionNumber.parse(project.version.toString())
    val nextVersion = "${v.major}.${v.minor}.${v.micro + 1}-SNAPSHOT"
    setVersion(nextVersion)
  }
}

tasks.register("printVersion") {
  doLast {
    println(project.version)
  }
}

license {
  header.set(resources.text.fromFile(rootProject.file("LICENSE-HEADER.txt")))
}

fun setVersion(version: String) {
  val properties = Properties()
  val propertiesFile = rootProject.file("gradle.properties")

  propertiesFile.inputStream().use { stream ->
    properties.load(stream)
  }

  if (properties["version"] == version) {
    println("project uses already version $version")
    return
  }

  println("set version from ${properties["version"]} to $version")

  properties["version"] = version
  propertiesFile.outputStream().use { stream ->
    properties.store(stream, "gradle properties")
  }
}
