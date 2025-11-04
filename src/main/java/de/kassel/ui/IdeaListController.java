package de.kassel.ui;

import de.kassel.db.IdeaRepository;
import de.kassel.db.TagRepository;
import de.kassel.model.Idea;
import de.kassel.model.IdeaRow;

import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class IdeaListController {

    @FXML private TextField filterField;

    @FXML private TableView<IdeaRow> table;
    @FXML private TableColumn<IdeaRow, String>  colTitle;
    @FXML private TableColumn<IdeaRow, Number>  colPriority;
    @FXML private TableColumn<IdeaRow, String>  colStatus;
    @FXML private TableColumn<IdeaRow, String>  colTags;

    private final IdeaRepository repo = new IdeaRepository();
    private String statusFilter = null;

    // Ungefilterte Masterliste (wird in reload() befüllt)
    private final javafx.collections.ObservableList<IdeaRow> masterRows =
            javafx.collections.FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colPriority.setCellValueFactory(new PropertyValueFactory<>("priority"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        if (colTags != null) {
            colTags.setCellValueFactory(new PropertyValueFactory<>("tags"));
        }
        reload();
        table.setOnKeyPressed(evt -> {
            switch (evt.getCode()) {
                case ENTER -> {
                    editSelectedRow();
                    evt.consume();
                }
                case DELETE -> {
                    deleteSelectedRow();
                    evt.consume();
                }
                default -> { /* nichts */ }
            }
        });
    }

    public void setStatusFilter(String status) {
        this.statusFilter = status;
        reload();
        setupContextMenu();
    }

    private boolean isTrashView() {
        return "trash".equalsIgnoreCase(statusFilter);
    }


    /** Lädt Daten aus dem Repo und füllt masterRows; Table zeigt zunächst alles. */
    private void reload() {
        // 1️⃣ Auswahl je nach Status (inkl. Papierkorb)
        var ideas =
                (statusFilter == null || statusFilter.equals("all"))
                        ? repo.findAll()
                        : (statusFilter.equals("trash")
                        ? repo.findTrash()
                        : repo.findByStatus(statusFilter));

        // 2️⃣ Tag-Namen vorbereiten
        var tagRepo = new TagRepository();

        // 3️⃣ Alte Liste löschen und neu füllen
        masterRows.clear();
        for (Idea i : ideas) {
            String tagNames = tagRepo.findTagsForIdea(i.id()).stream()
                    .map(de.kassel.model.Tag::name)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.joining(", "));

            var row = new IdeaRow(
                    i.id(),
                    i.title(),
                    i.priority().level(),
                    i.status().db()
            );
            row.setTags(tagNames);
            masterRows.add(row);
        }

        // 4️⃣ Anzeigen (alle sichtbar, Filter wird von MainController später angewendet)
        table.setItems(FXCollections.observableArrayList(masterRows));
    }

    /** Wird vom FXML (Suchfeld-Button) genutzt – filtert nur nach Text. */
    @FXML
    private void onApplyFilter() {
        applyFilter(filterField.getText(), java.util.Collections.emptyList());
    }

    /** Extern vom MainController aufrufbar: Text + ausgewählte Tags anwenden. */
    public void applyFilter(String query, java.util.List<String> selectedTags) {
        String q = (query == null) ? "" : query.trim().toLowerCase();
        boolean hasQuery = !q.isBlank();

        var tagNeedles = (selectedTags == null)
                ? java.util.List.<String>of()
                : selectedTags.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::toLowerCase)
                .toList();
        boolean hasTags = !tagNeedles.isEmpty();

        var filtered = masterRows.stream().filter(r -> {
            boolean matchesQuery = true;
            if (hasQuery) {
                String title  = (r.getTitle()  == null) ? "" : r.getTitle().toLowerCase();
                String status = (r.getStatus() == null) ? "" : r.getStatus().toLowerCase();
                String tags   = (r.getTags()   == null) ? "" : r.getTags().toLowerCase();
                String prio   = String.valueOf(r.getPriority());

                matchesQuery = title.contains(q) || status.contains(q) || tags.contains(q)
                        || ("p" + prio).equals(q) || prio.equals(q);
            }

            boolean matchesTags = true;
            if (hasTags) {
                String tags = (r.getTags() == null) ? "" : r.getTags().toLowerCase();
                // ANY-match: trifft, wenn irgendein ausgewählter Tag vorkommt
                matchesTags = tagNeedles.stream().anyMatch(tags::contains);
                // Für ALL-match stattdessen:
                // matchesTags = tagNeedles.stream().allMatch(tags::contains);
            }

            return matchesQuery && matchesTags;
        }).collect(Collectors.toCollection(FXCollections::observableArrayList));

        table.setItems(filtered);
    }

    // -------- Kontextmenü + Doppelklick --------
    private void setupContextMenu() {
        final ContextMenu menu = new ContextMenu();

        if (isTrashView()) {
            // ---- Menü NUR für Papierkorb ----
            MenuItem restore = new MenuItem("Wiederherstellen");
            restore.setOnAction(e -> {
                var sel = table.getSelectionModel().getSelectedItem();
                if (sel == null) return;
                repo.restore(sel.getId());      // ← muss in IdeaRepository existieren
                reload();
            });

            MenuItem deleteForever = new MenuItem("Endgültig löschen…");
            deleteForever.setOnAction(e -> {
                var sel = table.getSelectionModel().getSelectedItem();
                if (sel == null) return;

                var alert = new Alert(Alert.AlertType.CONFIRMATION,
                        "Eintrag endgültig löschen? Dies kann nicht rückgängig gemacht werden.",
                        ButtonType.OK, ButtonType.CANCEL);
                alert.setHeaderText(null);
                alert.showAndWait().ifPresent(bt -> {
                    if (bt == ButtonType.OK) {
                        repo.deletePermanent(sel.getId()); // ← harte Löschung
                        reload();
                    }
                });
            });

            menu.getItems().setAll(restore, new SeparatorMenuItem(), deleteForever);

        } else {
            // ---- Menü für alle normalen Ansichten ----
            MenuItem edit = new MenuItem("Bearbeiten…");
            edit.setOnAction(evt -> editSelectedRow());

            Menu editStatus = new Menu("Status ändern");
            for (String s : java.util.List.of("inbox", "draft", "doing", "done", "archived")) {
                MenuItem item = new MenuItem(s);
                item.setOnAction(ev -> {
                    var sel = table.getSelectionModel().getSelectedItem();
                    if (sel == null) return;
                    repo.updateStatus(sel.getId(), s);
                    reload();
                });
                editStatus.getItems().add(item);
            }

            MenuItem moveToTrash = new MenuItem("In Papierkorb verschieben…");
            moveToTrash.setOnAction(e -> {
                var sel = table.getSelectionModel().getSelectedItem();
                if (sel == null) return;

                var alert = new Alert(Alert.AlertType.CONFIRMATION,
                        "Eintrag wirklich in den Papierkorb verschieben?",
                        ButtonType.OK, ButtonType.CANCEL);
                alert.setHeaderText(null);
                alert.showAndWait().ifPresent(bt -> {
                    if (bt == ButtonType.OK) {
                        repo.moveToTrash(sel.getId());
                        reload();
                    }
                });
            });

            menu.getItems().setAll(edit, editStatus, new SeparatorMenuItem(), moveToTrash);
        }

        // RowFactory neu setzen, damit das gerade gebaute Menü greift
        table.setRowFactory(tv -> {
            var r = new TableRow<IdeaRow>();
            r.contextMenuProperty().bind(
                    Bindings.when(r.emptyProperty())
                            .then((ContextMenu) null)
                            .otherwise(menu)
            );
            // Doppelklick-Edit in der Trash-Ansicht deaktivieren
            r.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY
                        && event.getClickCount() == 2
                        && !r.isEmpty()
                        && !isTrashView()) {
                    editSelectedRow();
                    event.consume();
                }
            });
            return r;
        });
    }


    private void editSelectedRow() {
        var row = table.getSelectionModel().getSelectedItem();
        if (row == null) return;

        var opt = repo.findById(row.getId());
        if (opt.isEmpty()) {
            new Alert(Alert.AlertType.ERROR,
                    "Eintrag nicht gefunden (ID: " + row.getId() + ").").showAndWait();
            return;
        }
        openEditDialog(opt.get());
    }
    private void deleteSelectedRow() {
        var sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        var alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Eintrag wirklich löschen?", ButtonType.OK, ButtonType.CANCEL);
        alert.setHeaderText(null);
        alert.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.OK) {
                repo.delete(sel.getId());
                reload();
            }
        });
    }

    private void openEditDialog(de.kassel.model.Idea idea) {
        try {
            var url = MainApp.class.getResource("/de/kassel/ui/NewIdeaDialog.fxml");
            var loader = new FXMLLoader(Objects.requireNonNull(url));
            DialogPane content = loader.load();   // Root ist <DialogPane>

            var ctrl = loader.getController();
            if (ctrl instanceof NewIdeaDialogController dlg) {
                dlg.setInitial(idea);

                Dialog<de.kassel.model.Idea> dialog = new Dialog<>();
                dialog.setTitle("Idee bearbeiten");
                dialog.setDialogPane(content);
                dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

                Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
                dlg.bindOkDisable(okBtn);

                dialog.setResultConverter(bt -> bt == ButtonType.OK ? dlg.buildUpdatedOrShowError(idea) : null);

                dialog.showAndWait().ifPresent(updated -> {
                    // 1) Idee speichern
                    repo.update(updated);

                    // 2) Tags sichern
                    var selectedTags = dlg.getSelectedTagNames();
                    new de.kassel.db.TagRepository().replaceIdeaTags(updated.id(), selectedTags);

                    // 3) Reminder upsert/entfernen
                    Long when = dlg.getReminderEpochOrNull();
                    var remRepo = new de.kassel.db.ReminderRepository();
                    if (when != null) {
                        remRepo.upsertForIdea(updated.id(), when, updated.title());
                    } else {
                        remRepo.deleteForIdea(updated.id());
                    }

                    // 4) Attachments verarbeiten
                    var attRepo = new de.kassel.db.AttachmentRepository();

                    // 4a) zuerst löschen (nur bestehende IDs)
                    for (Long idToDel : dlg.getDeletedAttachmentIds()) {
                        if (idToDel != null) {
                            attRepo.deleteById(idToDel);
                        }
                    }

                    // 4b) neue Dateien aus dem Dialog speichern (Repo übernimmt Kopieren & Insert)
                    for (java.nio.file.Path src : dlg.getNewAttachmentPaths()) {
                        attRepo.insertFromPath(updated.id(), src);
                    }

                    // 5) UI aktualisieren
                    reload();
                });
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            new Alert(Alert.AlertType.ERROR,
                    "Fehler beim Öffnen des Bearbeiten-Dialogs: " + ex.getMessage()).showAndWait();
        }
    }


    /** Verträgt beide Varianten von findById: Optional<Idea> oder Idea. */
    @SuppressWarnings("unused")
    private Idea getIdeaOrNull(long id) {
        try {
            Optional<Idea> opt = repo.findById(id);
            return opt.orElse(null);
        } catch (NoSuchMethodError | NoClassDefFoundError ignored) {
        } catch (Throwable t) { }
        try {
            return (Idea) IdeaRepository.class
                    .getMethod("findById", long.class)
                    .invoke(repo, id);
        } catch (Exception ex) {
            return null;
        }
    }
}
