package de.kassel.ui;

import de.kassel.db.IdeaRepository;
import de.kassel.model.Idea;
import de.kassel.model.IdeaStatus;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.List;

public class KanbanController {

    @FXML private VBox colInbox;
    @FXML private VBox colDraft;
    @FXML private VBox colDoing;
    @FXML private VBox colDone;
    @FXML private VBox colArchived;

    private final IdeaRepository repo = new IdeaRepository();

    @FXML
    public void initialize() {
        // optionale Spalten-Header
        addHeader(colInbox,    "Inbox");
        addHeader(colDraft,    "Draft");
        addHeader(colDoing,    "Doing");
        addHeader(colDone,     "Done");
        addHeader(colArchived, "Archived");

        refresh();
    }

    @FXML
    private void onRefresh() {
        refresh();
    }

    private void refresh() {
        // Spalten leeren (Header in Position 0 behalten)
        clearColumn(colInbox);
        clearColumn(colDraft);
        clearColumn(colDoing);
        clearColumn(colDone);
        clearColumn(colArchived);

        // Daten laden und verteilen
        addIdeas(colInbox,    repo.findByStatus("inbox"));
        addIdeas(colDraft,    repo.findByStatus("draft"));
        addIdeas(colDoing,    repo.findByStatus("doing"));
        addIdeas(colDone,     repo.findByStatus("done"));
        addIdeas(colArchived, repo.findByStatus("archived"));
    }

    private void addIdeas(VBox column, List<Idea> ideas) {
        for (Idea i : ideas) {
            column.getChildren().add(makeCard(i));
        }
    }

    /** Mini-Karte für eine Idee. */
    private Node makeCard(Idea idea) {
        var box = new VBox(4);
        box.setStyle("""
            -fx-background-color: -fx-base;
            -fx-background-radius: 8;
            -fx-padding: 10;
            -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 6,0,0,2);
        """);

        var title = new Label(idea.title());
        title.setStyle("-fx-font-weight: bold;");

        var meta = new Label(
                "P" + idea.priority().level() + " • " + idea.status().name()
        );
        meta.setStyle("-fx-opacity: 0.7; -fx-font-size: 11;");

        box.getChildren().addAll(title, meta);

        // (später) DnD-Handler + Doppelklick zum Bearbeiten ergänzen
        box.setUserData(idea); // merken für spätere Aktionen
        return box;
    }

    private void addHeader(VBox column, String text) {
        var header = new Label(text);
        header.setStyle("-fx-font-weight: bold; -fx-padding: 0 0 6 0;");
        column.getChildren().add(header);
    }

    private void clearColumn(VBox column) {
        // Header (Index 0) stehen lassen
        if (column.getChildren().size() > 1) {
            column.getChildren().remove(1, column.getChildren().size());
        }
    }
}
