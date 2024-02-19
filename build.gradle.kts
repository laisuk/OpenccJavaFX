plugins {
    java
    application
    id("org.javamodularity.moduleplugin") version "1.8.12"
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("org.beryx.jlink") version "2.25.0"
}

group = "org.example"
version = "1.0-SNAPSHOT"

tasks.wrapper {
    // You can either download the binary-only version of Gradle (BIN) or
    // the full version (with sources and documentation) of Gradle (ALL)
    gradleVersion = "8.6"
    distributionType = Wrapper.DistributionType.ALL
}

repositories {
    mavenCentral()
}

val junitVersion = "5.10.1"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<JavaExec> {
    args("-Dfile.encoding=UTF-8")
}

application {
    mainModule.set("org.example.demofx")
    mainClass.set("org.example.demofx.DemoFxApplication")
}

javafx {
    version = "21.0.2"
    modules("javafx.controls", "javafx.fxml")
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    implementation(files("lib/OpenCC-Java.jar"))
//    implementation(fileTree("lib") {include("*.jar")})
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "org.example.demofx.DemoFxApplication"
    }
}

tasks.test {
    systemProperty("file.encoding", "utf-8")
    useJUnitPlatform()
}

jlink {
    imageZip = project.file("${layout.buildDirectory}/distributions/app-${javafx.platform.classifier}.zip")
    options = listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages")
    launcher {
        name = "app"
    }
}

tasks.named("jlinkZip") {
    group = "distribution"
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "org.example.demofx.DemoFxApplication"
    }
}