import org.gradle.internal.os.OperatingSystem

plugins {
    java
    application
    id("org.javamodularity.moduleplugin") version "1.8.15"
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("org.beryx.jlink") version "3.1.1"
}

group = "org.example"
version = "1.0.0"

tasks.wrapper {
    // Either download the binary-only version of Gradle (BIN) or
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
    mainModule.set("org.example.openccjavafx")
    mainClass.set("org.example.openccjavafx.OpenccJavaFxApplication")
}

javafx {
    version = "21.0.8"
    modules("javafx.controls", "javafx.fxml")
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    implementation("org.fxmisc.richtext:richtextfx:0.11.5")

    // JSON serialization/deserialization
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.1")
    implementation("com.fasterxml.jackson.core:jackson-core:2.19.1")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.19.1")

    // Core CLI parser
    implementation("info.picocli:picocli:4.7.7")
    annotationProcessor("info.picocli:picocli-codegen:4.7.7")
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "org.example.openccjavafx.OpenccJavaFxApplication"
    }
}

tasks.test {
    systemProperty("file.encoding", "utf-8")
    useJUnitPlatform()
}

val os: OperatingSystem? = OperatingSystem.current()
val isWindows = os?.isWindows
val isMac = os?.isMacOsX
val isLinux = os?.isLinux

// Notes: Use --compress=zip-6 for JDK 21+, otherwise use 2
jlink {
    imageZip = project.file("${layout.buildDirectory}/distributions/app-${javafx.platform.classifier}.zip")
    options = listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages")

    launcher {
        name = "OpenccJavaFX"
    }

    // ✅ Use this to tell jlink to include JavaFX modules
    addExtraDependencies("javafx")

    jpackage {
        imageName = "OpenccJavaFX"
        installerName = "OpenccJavaFX-Setup"
        appVersion = project.version.toString()

        // Choose installer type per OS (can override: -PinstallerType=deb|rpm|dmg|pkg|msi)
        val overrideType = (findProperty("installerType") as String?)?.lowercase()
        val targetType = overrideType ?: when {
            isWindows == true -> "msi"
            isMac == true -> "dmg"  // or "pkg" if you prefer
            else -> "deb"  // linux default
        }
        installerType = targetType

        // Allow overriding the installer output directory per invocation:
        val outOverride = (findProperty("installerOut") as String?)?.trim()
        if (!outOverride.isNullOrEmpty()) {
            installerOutputDir = file(outOverride)
        }

        // Optional icon per OS (will use if the file exists)
        val iconPath = when {
            isWindows == true -> "src/main/jpackage/icon.ico"
            isMac == true -> "src/main/jpackage/icon.icns"
            else -> "src/main/jpackage/icon.png"
        }

        if (file(iconPath).exists()) {
            icon = iconPath
        }

        // OS-specific installer options
        // IMPORTANT: add DEB maintainer (and a friendly linux package name)
        installerOptions = when {
            isWindows == true -> listOf("--win-menu", "--win-shortcut", "--win-dir-chooser")
            isMac == true -> listOf("--mac-package-identifier", "org.example.openccjavafx")
            else -> listOf(
                "--verbose",
                "--linux-shortcut",
                "--linux-package-name", "openccjavafx",
                "--linux-deb-maintainer", "Laisuk Lai <laisuk@users.noreply.github.com>",
                "--vendor", "OpenccJavaFX",
                "--linux-app-category", "Utility"
            )
        }

        // Optional: include external dicts folder in the final package
        // Keep resources (e.g., desktop files/icons)
        resourceDir = file("src/main/jpackage")
    }
}

val appImageRoot = layout.buildDirectory.dir(
    if (isMac == true) "jpackage/OpenccJavaFX.app/Contents/app"
    else "jpackage/OpenccJavaFX"
)

// Put dicts into the actual app-image for each OS
tasks.register<Copy>("copyDicts") {
    dependsOn("jpackageImage")
    from("dicts")
    into(appImageRoot.map { it.dir("dicts") })
}

tasks.named("jpackage").configure {
    dependsOn("copyDicts")
}

tasks.named("copyDicts") {
    mustRunAfter("jpackageImage")
}

// Zip the correct app-image path (bundle on macOS, folder elsewhere)
tasks.register<Zip>("zipAppImage") {
    dependsOn("jpackageImage", "copyDicts")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    archiveFileName.set("OpenccJavaFX-portable.zip")
    from(if (isMac == true) "build/jpackage/OpenccJavaFX.app" else "build/jpackage/OpenccJavaFX")
}

tasks.named("jlinkZip") {
    group = "distribution"
}

distributions {
    main {
        contents {
            from("dicts") {
                into("dicts")
            }
            from("bin/openccjava-cli.bat") {
                into("bin")
            }
            from("bin/openccjava-cli") {
                into("bin")
                filePermissions {
                    unix("0755") // Sets permissions to rwxr-xr-x
                }
            }
        }
    }
}
