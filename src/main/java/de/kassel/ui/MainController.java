package de.kassel.ui;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;

public class MainController {

    private static final java.util.Map<String, String> NAV_TO_STATUS = java.util.Map.of(
            "Inbox",   "inbox",
            "Draft",   "draft",
            "Doing",   "doing",
            "Done",    "done",
            "Archived","archived",
            "Alle Ideen","all"
    );

    // Hilfsfunktion in MainController ergänzen:
    private void loadIdeaListForStatus(String status) {
        try {
            var url = MainApp.class.getResource("/de/kassel/ui/IdeaListView.fxml");
            var loader = new javafx.fxml.FXMLLoader(java.util.Objects.requireNonNull(url));
            javafx.scene.Parent view = loader.load();

            // Controller holen und Parameter setzen
            var controller = loader.getController();
            if (controller instanceof IdeaListController c) {
                c.setStatusFilter(status);
            }

            contentPane.getChildren().setAll(view);
        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Fehler beim Laden der Liste (" + status + ")");
        }
    }

    @FXML private TextField searchField;
    @FXML private Label statusLabel;
    @FXML private ListView<String> navList;
    @FXML private ListView<String> tagList;
    @FXML private StackPane contentPane;

    @FXML
    public void initialize() {
        // Navigation befüllen
        navList.getItems().setAll("Inbox","Draft","Doing","Done","Archived","Alle Ideen");
        navList.getSelectionModel().selectFirst();
        //Laden der ersten Ansicht
        var first = navList.getSelectionModel().getSelectedItem();
        loadIdeaListForStatus(NAV_TO_STATUS.getOrDefault(first, "all"));
        statusLabel.setText("Bereit. " + first);

        navList.getSelectionModel().selectedItemProperty().addListener((obs,oldV,newV)
                ->{statusLabel.setText("Ansicht: " +newV);
            loadIdeaListForStatus(NAV_TO_STATUS.getOrDefault(newV,"all"));
        //hier später: Content wechseln (Table/Board)
        });

        //Tags (erstmal Dummmy, später aus DB)
        tagList.getItems().setAll("uni","projekt","privat");

        //statusLabel.setText("Bereit. " + navList.getSelectionModel().getSelectedItem());

        try {
            var url  = MainApp.class.getResource("/de/kassel/ui/IdeaListView.fxml");
            javafx.scene.Parent view = javafx.fxml.FXMLLoader.load(
                    java.util.Objects.requireNonNull(url)
            );
            contentPane.getChildren().setAll(view);
        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Fehler beim Laden der Idea-Ansicht!");
        }
    }

    @FXML
    private void onNewIdea(){
        statusLabel.setText("Neue Idee anlegen...");
        //später: Dialog -> Idea speichern -> Liste refresh
    }

    @FXML
    private void onSearch(){
        String q = searchField.getText();
        statusLabel.setText(q== null|| q.isBlank() ? "Suche: (leer)": "Suche: " + q);
        //später: Query-DSL parsen -> Ergebnisliste laden
    }

    @FXML
    private void onOpenSettings() {
        statusLabel.setText("Einstellungen öffnen....");
        //später: Settings-Dialog
    }
}
