import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.util.VersionNumber
import java.util.Properties
import java.io.File

plugins {
    // Apply the Java Gradle plugin development plugin to add support for developing Gradle plugins
    `java-gradle-plugin`

    // Apply the Groovy plugin to add support for Groovy
    groovy

    // Add maven publishing plugin for testing with local maven repository
    `maven-publish`

    // publishing plugin
    id("com.gradle.plugin-publish") version "0.12.0"

    id("org.scm-manager.license") version "0.8.0"
}

repositories {
    // Use maven central for resolving dependencies.
    // You can declare any Maven/Ivy/file repository here.
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

project.tasks.withType<GroovyCompile> {
    sourceCompatibility = JavaVersion.VERSION_17.toString()
    targetCompatibility = JavaVersion.VERSION_17.toString()
}

project.tasks.withType<JavaCompile> {
    sourceCompatibility = JavaVersion.VERSION_17.toString()
    targetCompatibility = JavaVersion.VERSION_17.toString()
}

tasks.test {
    useJUnitPlatform()
}

gradlePlugin {
    // Define the plugin
    plugins {
        create("smpPlugin") {
            id = "org.scm-manager.smp"
            implementationClass = "com.cloudogu.smp.GradleSmpPlugin"
        }
    }
}

// Add a source set for the functional test suite
sourceSets {
    create("functionalTest") {
    }
}

gradlePlugin.testSourceSets(sourceSets["functionalTest"])
configurations["functionalTestImplementation"].extendsFrom(configurations["testImplementation"])

// Add a task to run the functional tests
tasks.register<Test>("functionalTest") {
    testClassesDirs = sourceSets["functionalTest"].output.classesDirs
    classpath = sourceSets["functionalTest"].runtimeClasspath
    useJUnitPlatform()
}

tasks.check {
    // Run the functional tests as part of `check`
    dependsOn(tasks.named("functionalTest"))
    dependsOn(tasks.named("checkLicenses"))
}

// publish plugin
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

// release tasks

tasks.register("setVersion") {
    doLast {
        if (!project.hasProperty("newVersion")) {
            throw GradleException("usage setVersion -PnewVersion=x.y.z")
        }

        val version = project.property("newVersion") as String
        setVersion(version)
    }
}

tasks.register("setVersionToNextSnapshot") {
    doLast {
        val v = VersionNumber.parse(project.version as String)
        val version = "${v.major}.${v.minor}.${v.micro + 1}-SNAPSHOT"
        setVersion(version)
    }
}

tasks.register("printVersion") {
    doLast {
        println(project.version)
    }
}

extensions.getByName("license").apply {
    val setHeaderMethod = this.javaClass.getMethod("setHeader", File::class.java)
    setHeaderMethod.invoke(this, project.rootProject.file("LICENSE-HEADER.txt"))
}

fun setVersion(version: String) {
    val properties = Properties()

    val propertiesFile = File(project.rootDir, "gradle.properties")
    propertiesFile.inputStream().use { stream ->
        properties.load(stream)
    }

    if (properties.getProperty("version") == version) {
        println("project uses already version $version")
        return
    }

    println("set version from ${properties.getProperty("version")} to $version")

    properties.setProperty("version", version)
    propertiesFile.outputStream().use { stream ->
        properties.store(stream, "gradle properties")
    }
}
