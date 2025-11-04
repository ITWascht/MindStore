package de.kassel.ui;

import de.kassel.model.Idea;
import de.kassel.model.IdeaStatus;
import de.kassel.model.Priority;
import de.kassel.db.TagRepository;
import de.kassel.model.Tag;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import javafx.scene.control.SelectionMode;

import java.nio.file.Path;
import java.nio.file.Paths;

public class NewIdeaDialogController {

    @FXML private TextField titleField;
    @FXML private TextArea bodyArea;
    @FXML private ComboBox<Priority> priorityBox;
    @FXML private ComboBox<IdeaStatus> statusBox;
    @FXML private TextField effortField;
    @FXML private Label errorLabel;

    @FXML private TextField newTagField;
    @FXML private ListView<Tag> tagsList;

    // Reminder
    @FXML private DatePicker reminderDate;
    @FXML private TextField  reminderTime;

    // Attachments-UI (ListView zeigt AttachmentRow)
    @FXML private ListView<AttachmentRow> attachmentsList;

    private final TagRepository tagRepo = new TagRepository();
    private final de.kassel.db.AttachmentRepository attachmentRepo = new de.kassel.db.AttachmentRepository();

    // Temporär: neue (noch nicht gespeicherte) Dateipfade
    private final java.util.List<Path> pendingFiles = new java.util.ArrayList<>();
    // Zu löschende bestehende Attachment-IDs (bei Bearbeiten)
    private final java.util.List<Long> deletedAttachmentIds = new java.util.ArrayList<>();

    private final ObservableList<AttachmentRow> attachments = FXCollections.observableArrayList();

    /** Zeilendarstellung für die ListView (id=null => neu; sonst vorhanden). */
    public static class AttachmentRow {
        public final Long id;    // null => neu
        public final Path path;  // Dateipfad
        public final String name;

        public AttachmentRow(Long id, Path path, String name) {
            this.id = id; this.path = path; this.name = name;
        }
        @Override public String toString() { return name; }
    }

    @FXML
    public void initialize() {
        // Enums
        priorityBox.getItems().setAll(Priority.values());
        statusBox.getItems().setAll(IdeaStatus.values());

        // Defaults
        priorityBox.getSelectionModel().select(Priority.P2);
        statusBox.getSelectionModel().select(IdeaStatus.INBOX);

        // Aufwand nur Ziffern
        effortField.textProperty().addListener((obs, o, n) -> {
            if (n != null && !n.isBlank() && !n.matches("\\d{0,9}")) {
                effortField.setText(o);
            }
        });

        // Tags
        var allTags = tagRepo.findAll();
        tagsList.setItems(FXCollections.observableArrayList(allTags));
        tagsList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tagsList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Tag item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name());
            }
        });

        // Attachments
        attachmentsList.setItems(attachments);
        attachmentsList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        errorLabel.setText("");
    }

    /** Validierung + neues Idea-Objekt (ohne id/createdAt). */
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
        return new Idea(title, body, prio, status, effort);
    }

    /** Aktualisiertes Idea anhand bestehender Idea (id/createdAt bleiben). */
    public de.kassel.model.Idea buildUpdatedOrShowError(de.kassel.model.Idea original) {
        var tmp = buildIdeaOrShowError();
        if (tmp == null) return null;

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

    /** Dialog für Bearbeiten vorbefüllen. */
    public void setInitial(de.kassel.model.Idea idea) {
        // Basis
        titleField.setText(idea.title());
        bodyArea.setText(idea.body());
        priorityBox.getSelectionModel().select(idea.priority());
        statusBox.getSelectionModel().select(idea.status());
        effortField.setText(idea.effortMinutes() == null ? "" : String.valueOf(idea.effortMinutes()));

        // Tags selektieren
        var current = tagRepo.findTagsForIdea(idea.id());
        var selModel = tagsList.getSelectionModel();
        selModel.clearSelection();
        for (var t : current) {
            tagsList.getItems().stream()
                    .filter(it -> it.id() == t.id())
                    .findFirst()
                    .ifPresent(selModel::select);
        }

        // Attachments laden
        attachments.clear();
        deletedAttachmentIds.clear();
        var existing = attachmentRepo.listForIdea(idea.id());
        for (var a : existing) {
            attachments.add(new AttachmentRow(
                    a.id(),
                    Paths.get(a.filePath()),
                    a.fileName()
            ));
        }

        // Reminder laden (optional)
        var remOpt = new de.kassel.db.ReminderRepository().findByIdeaId(idea.id());
        remOpt.ifPresent(rem -> {
            var zdt = java.time.Instant.ofEpochSecond(rem.dueAt())
                    .atZone(java.time.ZoneId.systemDefault());
            reminderDate.setValue(zdt.toLocalDate());
            reminderTime.setText(zdt.toLocalTime().toString().substring(0, 5));
        });
    }

    // ---- Tags ----
    @FXML
    private void onAddTag() {
        String raw = newTagField.getText();
        if (raw == null) return;
        String name = raw.trim();
        if (name.isEmpty()) return;

        var tag = tagRepo.ensureExists(name);
        if (tagsList.getItems().stream().noneMatch(t -> t.id() == tag.id())) {
            tagsList.getItems().add(tag);
        }
        tagsList.getSelectionModel().select(tag);
        newTagField.clear();
    }

    public java.util.List<String> getSelectedTagNames() {
        return tagsList.getSelectionModel().getSelectedItems()
                .stream().map(Tag::name).toList();
    }

    // ---- Reminder ----
    public Long getReminderEpochOrNull() {
        var d = reminderDate == null ? null : reminderDate.getValue();
        var tStr = reminderTime == null ? null : reminderTime.getText();
        if (d == null || tStr == null || tStr.isBlank()) return null;

        java.time.LocalTime t;
        try {
            t = java.time.LocalTime.parse(tStr.trim()); // HH:mm
        } catch (Exception ex) {
            errorLabel.setText("Zeitformat HH:mm (z. B. 16:00).");
            return null;
        }

        var dt = java.time.LocalDateTime.of(d, t);
        var z  = java.time.ZoneId.systemDefault();
        return dt.atZone(z).toEpochSecond();
    }

    // ---- Attachments (Buttons im Dialog) ----
    @FXML private void onAddAttachment() {
        var chooser = new javafx.stage.FileChooser();
        var file = chooser.showOpenDialog(attachmentsList.getScene().getWindow());
        if (file == null) return;

        var p = file.toPath();
        pendingFiles.add(p);

        attachments.add(new AttachmentRow(
                null,                         // id=null -> neu
                p.toAbsolutePath(),
                p.getFileName().toString()
        ));
    }

    @FXML private void onRemoveAttachment() {
        var sel = attachmentsList.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        attachments.remove(sel);

        // Falls es ein bestehender Datensatz war: als "zu löschen" merken
        if (sel.id != null) {
            deletedAttachmentIds.add(sel.id);
        } else {
            // war nur neu ausgewählt: aus pendingFiles raus
            pendingFiles.removeIf(p -> p.equals(sel.path));
        }
    }

    @FXML private void onOpenAttachment() {
        var sel = attachmentsList.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        try {
            java.awt.Desktop.getDesktop().open(sel.path.toFile());
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Kann Anhang nicht öffnen.").showAndWait();
        }
    }

    /** Neue (noch nicht gespeicherte) Dateipfade, die nach Insert/Update in die DB müssen. */
    public java.util.List<Path> getNewAttachmentPaths() {
        return java.util.List.copyOf(pendingFiles);
    }

    /** IDs bereits vorhandener Attachments, die der Nutzer im Dialog entfernt hat. */
    public java.util.List<Long> getDeletedAttachmentIds() {
        return java.util.List.copyOf(deletedAttachmentIds);
    }
}
