package de.kassel.ui;

import de.kassel.model.Idea;
import de.kassel.model.IdeaStatus;
import de.kassel.model.Priority;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.collections.FXCollections;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;

import de.kassel.db.TagRepository;
import de.kassel.model.Tag;

public class NewIdeaDialogController {

    @FXML private TextField titleField;
    @FXML private TextArea bodyArea;
    @FXML private ComboBox<Priority> priorityBox;
    @FXML private ComboBox<IdeaStatus> statusBox;
    @FXML private TextField effortField;
    @FXML private Label errorLabel;
    @FXML private TextField newTagField;
    @FXML private javafx.scene.control.ListView<de.kassel.model.Tag> tagsList;
    @FXML private javafx.scene.control.DatePicker reminderDate;
    @FXML private javafx.scene.control.TextField  reminderTime;

    private final TagRepository tagRepo = new TagRepository();

    @FXML
    public void initialize() {
        // Enums befüllen
        priorityBox.getItems().setAll(Priority.values());
        statusBox.getItems().setAll(IdeaStatus.values());

        // Defaults
        priorityBox.getSelectionModel().select(Priority.P2);
        statusBox.getSelectionModel().select(IdeaStatus.INBOX);

        // einfache Input-Beschränkung für die Zahl
        effortField.textProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && !newV.isBlank() && !newV.matches("\\d{0,9}")) {
                effortField.setText(oldV);
            }
        });

        var allTags = tagRepo.findAll();                 // List<Tag>
        tagsList.setItems(FXCollections.observableArrayList(allTags));
        tagsList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Optional: einfache Zellanzeige nur mit dem Namen
        tagsList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Tag item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name());
            }
        });

        errorLabel.setText("");
    }

    /** Validiert Felder und baut ein Idea-Objekt (ohne id/createdAt). */
    public Idea buildIdeaOrShowError() {
        String title = titleField.getText() == null ? "" : titleField.getText().trim();
        if (title.isBlank()) {
            errorLabel.setText("Titel darf nicht leer sein.");
            return null;
        }
        String body = bodyArea.getText() == null ? "" : bodyArea.getText().trim();

        Priority prio = priorityBox.getValue() == null ? Priority.P2 : priorityBox.getValue();
        IdeaStatus status = statusBox.getValue() == null ? IdeaStatus.INBOX : statusBox.getValue();

        Integer effort = null;
        String effortStr = effortField.getText();
        if (effortStr != null && !effortStr.isBlank()) {
            try {
                effort = Integer.parseInt(effortStr);
                if (effort < 0) {
                    errorLabel.setText("Aufwand muss >= 0 sein.");
                    return null;
                }
            } catch (NumberFormatException ex) {
                errorLabel.setText("Aufwand muss eine Zahl sein.");
                return null;
            }
        }

        errorLabel.setText("");
        // Komfort-Konstruktor: (title, body, priority, status, effortMinutes)
        return new Idea(title, body, prio, status, effort);
    }

    /** Baut aus den Eingaben eine aktualisierte Idea auf Basis der Original-Idea (id/createdAt bleiben). */
    public de.kassel.model.Idea buildUpdatedOrShowError(de.kassel.model.Idea original) {
        var tmp = buildIdeaOrShowError(); // nutzt deine bestehende Validierung
        if (tmp == null) return null;

        // updated_at wird vom Trigger gesetzt – hier auf null lassen
        return new de.kassel.model.Idea(
                original.id(),
                tmp.title(),
                tmp.body(),
                tmp.priority(),
                tmp.status(),
                tmp.effortMinutes(),
                original.createdAt(),
                null
        );
    }

    public void bindOkDisable(Button okButton) {
        okButton.disableProperty().bind(titleField.textProperty().isEmpty());
    }

    /** Füllt den Dialog mit den Werten einer bestehenden Idee (für "Bearbeiten"). */
    public void setInitial(de.kassel.model.Idea idea) {
        // Basis-Felder
        titleField.setText(idea.title());
        bodyArea.setText(idea.body());
        priorityBox.getSelectionModel().select(idea.priority());
        statusBox.getSelectionModel().select(idea.status());
        effortField.setText(idea.effortMinutes() == null ? "" : String.valueOf(idea.effortMinutes()));

        // Tags vorselektieren
        var current = tagRepo.findTagsForIdea(idea.id()); // List<Tag>
        var selModel = tagsList.getSelectionModel();
        selModel.clearSelection();

        for (var t : current) {
            tagsList.getItems().stream()
                    .filter(it -> it.id() == t.id())
                    .findFirst()
                    .ifPresent(selModel::select);
        }

        // Reminder (falls vorhanden) vorfüllen
        var remOpt = new de.kassel.db.ReminderRepository().findByIdeaId(idea.id());
        remOpt.ifPresent(rem -> {
            var zdt = java.time.Instant.ofEpochSecond(rem.dueAt())
                    .atZone(java.time.ZoneId.systemDefault());
            reminderDate.setValue(zdt.toLocalDate());
            // "HH:mm" (erste 5 Zeichen)
            reminderTime.setText(zdt.toLocalTime().toString().substring(0, 5));
        });
    }


    @FXML
    private void onAddTag() {
        String raw = newTagField.getText();
        if (raw == null) return;
        String name = raw.trim();
        if (name.isEmpty()) return;

        // Tag erstellen/holen (idempotent)
        var tag = tagRepo.ensureExists(name);

        // Liste aktualisieren (falls neu) und selektieren
        if (tagsList.getItems().stream().noneMatch(t -> t.id() == tag.id())) {
            tagsList.getItems().add(tag);
        }
        tagsList.getSelectionModel().select(tag);

        newTagField.clear();
    }

    public java.util.List<String> getSelectedTagNames() {
        return tagsList.getSelectionModel().getSelectedItems()
                .stream().map(Tag::name)
                .toList();
    }

    public Long getReminderEpochOrNull() {
        var d = reminderDate == null ? null : reminderDate.getValue();
        var tStr = reminderTime == null ? null : reminderTime.getText();

        if (d == null || tStr == null || tStr.isBlank()) return null;

        java.time.LocalTime t;
        try {
            t = java.time.LocalTime.parse(tStr.trim()); // erwartet HH:mm
        } catch (Exception ex) {
            errorLabel.setText("Zeitformat HH:mm (z. B. 16:00).");
            return null;
        }

        var dt = java.time.LocalDateTime.of(d, t);
        var z  = java.time.ZoneId.systemDefault();
        return dt.atZone(z).toEpochSecond();
    }



}
