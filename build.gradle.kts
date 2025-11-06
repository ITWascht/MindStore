plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("org.beryx.jlink") version "3.0.1"
}

repositories { mavenCentral() }

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(24))
}

dependencies {
    implementation("org.openjfx:javafx-base:24:win")
    implementation("org.openjfx:javafx-graphics:24:win")
    implementation("org.openjfx:javafx-controls:24:win")
    implementation("org.openjfx:javafx-fxml:24")

    implementation("org.xerial:sqlite-jdbc:3.46.0.1")
    implementation("org.slf4j:slf4j-simple:2.0.16")
}

application {
    mainClass.set("de.kassel.ui.MainApp")
    mainModule.set("de.kassel.mindstore")
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

javafx {
    version = "24"
    modules = listOf("javafx.controls", "javafx.fxml")
}

tasks.withType<JavaCompile>  { options.encoding = "UTF-8" }
tasks.withType<ProcessResources> { filteringCharset = "UTF-8" }

//
// ⬇️ Hier kommt der neue Manifest-Block hin — also außerhalb des jlink!
//


        tasks.withType<Jar> {
            val runtimeJars = configurations.runtimeClasspath.get()
                .files
                .map { it.name }
                .filterNot { it.equals("${project.name}.jar", ignoreCase = true) }

            manifest {
                attributes(
                    "Main-Class" to "de.kassel.ui.MainApp",
                    "Class-Path" to runtimeJars.joinToString(" ")
                )
            }
        }

//
// ⬇️ Erst danach kommt der jlink-Block
//
jlink {
    // jlink-Optionen: hier können wir auch --add-modules für jlink selbst setzen
    options = listOf(
        "--strip-debug",
        "--compress=1",
        "--no-header-files",
        "--no-man-pages",
        // JavaFX-Module explizit dem jlink-Aufruf mitgeben:
        "--add-modules", "javafx.controls,javafx.fxml"
    )

    launcher {
        name = "MindStore"
        // Java-Startargumente des Launchers (zur Laufzeit):
        jvmArgs = listOf(
            "--enable-native-access=ALL-UNNAMED",
            "--enable-native-access=javafx.graphics"
        )
    }

    jpackage {
        imageName     = "MindStore"
        installerName = "MindStore"
        installerType = "exe"
        appVersion    = "1.0.0"
        vendor        = "Sebastian"

        // WICHTIG: Dem erzeugten Launcher auch zur Laufzeit Modulpfad + Module mitgeben.
        // (getrennte Tokens; so versteht es jpackage/Launcher zuverlässig)
        jvmArgs = listOf(
            "--module-path", "%APPDIR%\\app",
            "--add-modules", "javafx.controls,javafx.fxml",
            "--enable-native-access=ALL-UNNAMED",
            "--enable-native-access=javafx.graphics",
            "-Djdbc.drivers=org.sqlite.JDBC"
        )

        // Konsole NUR fürs App-Image
        imageOptions = listOf("--win-console")

        // Windows-Installer-Optionen (ohne --win-console)
        installerOptions = listOf(
            "--win-menu",
            "--win-shortcut",
            "--win-dir-chooser"
        )
    }
}


tasks.named<org.gradle.jvm.tasks.Jar>("jar") {
    val runtimeJars = configurations.runtimeClasspath.get()
        .files
        .map { it.name }
        .filterNot { it.equals("${project.name}.jar", ignoreCase = true) }

    manifest {
        attributes(
            "Main-Class" to "de.kassel.ui.MainApp",
            "Class-Path" to runtimeJars.joinToString(" ")
        )
    }
}
