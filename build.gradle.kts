plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

repositories { mavenCentral() }

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(24))
}

dependencies {
    implementation("org.openjfx:javafx-controls:24")
    implementation("org.openjfx:javafx-fxml:24")
    implementation("org.xerial:sqlite-jdbc:3.46.0.1")
    // optional, um SLF4J-Warnung loszuwerden:
    implementation("org.slf4j:slf4j-simple:2.0.16")
}

application {
    mainClass.set("de.kassel.ui.MainApp")
    applicationDefaultJvmArgs = listOf(
        "--enable-native-access=javafx.graphics",
        "--enable-native-access=ALL-UNNAMED"
    )
}

javafx {
    version = "24"
    modules = listOf("javafx.controls", "javafx.fxml")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
tasks.withType<ProcessResources> {
    filteringCharset = "UTF-8"
}