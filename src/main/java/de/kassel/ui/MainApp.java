package de.kassel.ui;

import de.kassel.db.DbManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

import java.util.Objects;

public class MainApp extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        // DB initialisieren + Diagnose
        try (var c = DbManager.getConnection()) {
            System.out.println("SQLite URL = " + c.getMetaData().getURL());
        } catch (Exception e) {
            System.err.println("DB-Init failed: " + e.getClass().getName() + " - " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("Root cause: " + e.getCause().getClass().getName() + " - " + e.getCause().getMessage());
            }
            throw e;
        }

        FXMLLoader loader = new FXMLLoader(
                java.util.Objects.requireNonNull(MainApp.class.getResource("/de/kassel/ui/MainView.fxml"))
        );
        Parent root = loader.load();
        MainController controller = loader.getController();
        Scene scene = new Scene(root, 900, 600);

// Ctrl+N → Neue Idee
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN),
                () -> controller.onNewIdea()
        );

        scene.getStylesheets().add(
                java.util.Objects.requireNonNull(MainApp.class.getResource("/de/kassel/ui/app.css")).toExternalForm()
        );

        stage.setTitle("MindStore");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) { launch(args); }
}
