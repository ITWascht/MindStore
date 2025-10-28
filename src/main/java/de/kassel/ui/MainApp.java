package de.kassel.ui;

import de.kassel.db.DbManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.util.Objects;

public class MainApp extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        // DB initialisieren (legt Datei + Schema + Seed an)
        // 1) DB initialisieren + Diagnose
        try (var c = DbManager.getConnection()) {
            System.out.println("SQLite URL = " + c.getMetaData().getURL());
        } catch (Exception e) {
            System.err.println("DB-Init failed: " + e.getClass().getName() + " - " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("   Root cause: " + e.getCause().getClass().getName() + " - " + e.getCause().getMessage());
            }
            throw e; // damit wir den Stacktrace sehen
        }

        // FXML laden (Parent statt Object!)
        FXMLLoader loader = new FXMLLoader(
                Objects.requireNonNull(MainApp.class.getResource("/de/kassel/ui/MainView.fxml"))
        );
        Parent root = loader.load();

        Scene scene = new Scene(root, 900, 600);
        scene.getStylesheets().add(
                Objects.requireNonNull(MainApp.class.getResource("/de/kassel/ui/app.css")).toExternalForm()
        );

        stage.setTitle("MindStore");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
