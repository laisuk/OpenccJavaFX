plugins {
    java
    application
    id("org.javamodularity.moduleplugin") version "1.8.15"
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("org.beryx.jlink") version "3.0.1"
}

group = "org.example"
version = "1.0-SNAPSHOT"

tasks.wrapper {
    // You can either download the binary-only version of Gradle (BIN) or
    // the full version (with sources and documentation) of Gradle (ALL)
    gradleVersion = "8.11.1"
    distributionType = Wrapper.DistributionType.ALL
}

repositories {
    mavenCentral()
}

val junitVersion = "5.10.3"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<JavaExec> {
    jvmArgs = listOf(
        "--enable-native-access=javafx.graphics",
        "-Dfile.encoding=UTF-8"
    )
}

application {
    mainModule.set("org.example.openccfx")
    mainClass.set("org.example.openccfx.OpenccFxApplication")
}

javafx {
    version = "23.0.2"
    modules("javafx.controls", "javafx.fxml")
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    implementation("org.fxmisc.richtext:richtextfx:0.11.5")
//    implementation(fileTree("lib") {include("*.jar")})
    // JSON serialization/deserialization
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.1")
    implementation("com.fasterxml.jackson.core:jackson-core:2.19.1")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.19.1")
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "org.example.openccfx.OpenccFxApplication"
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
        attributes["Main-Class"] = "org.example.openccfx.OpenccFxApplication"
    }
}

distributions {
    main {
        contents {
            from("dicts") {
                into("dicts")
            }
        }
    }
}
