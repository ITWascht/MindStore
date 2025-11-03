package de.kassel.ui;

import de.kassel.db.TagRepository;
import de.kassel.model.Tag;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.Comparator;
import java.util.List;

public class TagManagerController {

    @FXML private ListView<Tag> assignedList;
    @FXML private ListView<Tag> allList;
    @FXML private TextField newTagField;
    @FXML private Label errorLabel;

    private final TagRepository repo = new TagRepository();
    private long ideaId;

    /** Vom aufrufenden Code setzen, bevor der Dialog gezeigt wird. */
    public void setIdeaId(long ideaId) {
        this.ideaId = ideaId;
        reload();
    }

    @FXML
    public void initialize() {
        assignedList.setCellFactory(lv -> new TagCell());
        allList.setCellFactory(lv -> new TagCell());
        errorLabel.setText("");
    }

    private void reload() {
        List<Tag> assigned = repo.findTagsForIdea(ideaId);
        List<Tag> all = repo.findAll();

        // assigned + all sortieren
        assigned.sort(Comparator.comparing(Tag::name, String.CASE_INSENSITIVE_ORDER));
        all.sort(Comparator.comparing(Tag::name, String.CASE_INSENSITIVE_ORDER));

        // All minus Assigned in rechter Liste anzeigen
        var assignedIds = assigned.stream().map(Tag::id).collect(java.util.stream.Collectors.toSet());
        var available = new java.util.ArrayList<Tag>();
        for (Tag t : all) if (!assignedIds.contains(t.id())) available.add(t);

        assignedList.getItems().setAll(assigned);
        allList.getItems().setAll(available);
    }

    @FXML
    private void onAddExisting() {
        Tag sel = allList.getSelectionModel().getSelectedItem();
        if (sel == null) { setError("Bitte rechts einen Tag wählen."); return; }
        repo.addTagToIdea(ideaId, sel.id());
        setError("");
        reload();
    }

    @FXML
    private void onRemove() {
        Tag sel = assignedList.getSelectionModel().getSelectedItem();
        if (sel == null) { setError("Bitte links einen Tag wählen."); return; }
        repo.removeTagFromIdea(ideaId, sel.id());
        setError("");
        reload();
    }

    @FXML
    private void onCreateAndAssign() {
        String name = newTagField.getText();
        if (name == null || name.isBlank()) {
            setError("Tag-Name darf nicht leer sein.");
            return;
        }
        final String finalName = name.trim(); // <- jetzt final

        try {
            // Versuche, bestehenden Tag gleichen Namens zu finden
            Tag existing = repo.findAll().stream()
                    .filter(t -> t.name().equalsIgnoreCase(finalName))
                    .findFirst()
                    .orElse(null);

            long tagId;
            if (existing == null) {
                tagId = repo.insert(new Tag(finalName, null)); // ohne Farbe
            } else {
                tagId = existing.id();
            }

            repo.addTagToIdea(ideaId, tagId);
            newTagField.clear();
            setError("");
            reload();
        } catch (Exception ex) {
            setError("Tag konnte nicht erstellt/zugewiesen werden: " + ex.getMessage());
        }
    }

    private void setError(String msg) { errorLabel.setText(msg == null ? "" : msg); }

    /** Einfache Zellen-UI: Name + (optional) Farbe anzeigen */
    private static class TagCell extends ListCell<Tag> {
        @Override protected void updateItem(Tag item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) { setText(null); setGraphic(null); return; }
            String text = item.name();
            if (item.color() != null && !item.color().isBlank()) {
                text += "  [" + item.color() + "]";
            }
            setText(text);
        }
    }
}
