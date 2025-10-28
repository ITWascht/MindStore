plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

repositories {
    mavenCentral()
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(24))
}

application {
    // Passe den Pfad gleich an, wenn du die Main-Klasse anlegst
    mainClass.set("de.kassel.ui.MainApp")
}
javafx {
    version="24"
    modules = listOf("javafx.controls","javafx.fxml")
}

dependencies {
    // SQLite JDBC (embedded)
    implementation("org.xerial:sqlite-jdbc:3.46.0.1")
    // (später kommen JavaFX & Logging dazu)
}
