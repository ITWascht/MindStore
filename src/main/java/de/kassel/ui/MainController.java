package de.kassel.ui;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.ListChangeListener;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import java.util.*;
import javafx.scene.layout.Region;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.UUID;
import java.util.Objects;

import de.kassel.settings.SettingsStore;
import de.kassel.settings.AppSettings;

public class MainController {

    // Referenz auf den aktuell geladenen Listen-Controller (für Filter-Aufrufe)
    private IdeaListController currentList;

    private static final java.util.Map<String, String> NAV_TO_STATUS = java.util.Map.of(
            "Inbox",       "inbox",
            "Draft",       "draft",
            "Doing",       "doing",
            "Done",        "done",
            "Archived",    "archived",
            "Alle Ideen",  "all",
            "Papierkorb",  "trash"
    );

    @FXML private TextField       searchField;
    @FXML private Label           statusLabel;
    @FXML private ListView<String> navList;
    @FXML private ListView<String> tagList;
    @FXML private StackPane       contentPane;

    // Reminder UI
    @FXML private ListView<ReminderRow>   reminderList;

    //Settings
    private de.kassel.settings.AppSettings settings;
    private javafx.animation.Timeline reminderTimeline;

    // Lädt die Listen-View, setzt Status-Filter und wendet aktuelle Filter (Suche/Tags) an
    private void loadIdeaListForStatus(String status) {
        try {
            var url = MainApp.class.getResource("/de/kassel/ui/IdeaListView.fxml");
//            System.out.println("MainView URL = " + url);
            var loader = new FXMLLoader(Objects.requireNonNull(url));
            Parent view = loader.load();

            // Controller merken und sofort konfigurieren
            currentList = loader.getController();
            if (currentList != null) {
                currentList.setStatusFilter(status);
                applyActiveFilters(); // Suche/Tags direkt anwenden
            }

            contentPane.getChildren().setAll(view);
        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Fehler beim Laden der Liste (" + status + ")");
        }
    }

    @FXML
    public void initialize() {
        // Navigation befüllen (muss VOR der Auswahl passieren)
        navList.getItems().setAll("Inbox","Draft","Doing","Done","Archived","Alle Ideen","Papierkorb","Board");

// 1) gewünschte Startansicht als NAV-LABEL ermitteln (z. B. "Inbox", "Board", …)
        String preferred = resolveStartViewFromSettings();

// 2) Fallback, falls Label nicht existiert
        if (!navList.getItems().contains(preferred)) {
            preferred = "Alle Ideen";
        }

// 3) Auswahl setzen und passende Ansicht laden
        navList.getSelectionModel().select(preferred);
        if ("Board".equalsIgnoreCase(preferred)) {
            loadKanban();
            statusLabel.setText("Ansicht: Board");
        } else {
            // NAV_TO_STATUS mappt Label -> status-key ("inbox", "all", …)
            loadIdeaListForStatus(NAV_TO_STATUS.getOrDefault(preferred, "all"));
            statusLabel.setText("Bereit. " + preferred);
        }

// 4) Listener für spätere Wechsel
        navList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            statusLabel.setText("Ansicht: " + newV);
            if ("Board".equalsIgnoreCase(newV)) {
                loadKanban();
                return;
            }
            loadIdeaListForStatus(NAV_TO_STATUS.getOrDefault(newV, "all"));
        });

        // Tags aus DB laden und sortieren
        var tagRepo = new de.kassel.db.TagRepository();
        var tagNames = tagRepo.findAll().stream()
                .map(de.kassel.model.Tag::name)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        tagList.getItems().setAll(tagNames);
        tagList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Live-Filter: Suche
        searchField.textProperty().addListener((obs, o, n) -> applyActiveFilters());

        // Live-Filter: Änderungen an Tag-Auswahl
        tagList.getSelectionModel().getSelectedItems().addListener(
                (ListChangeListener<String>) c -> applyActiveFilters()
        );

        // Toggle-Auswahl per erneutem Klick (an/abwählen)
        tagList.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            var node = e.getPickResult().getIntersectedNode();
            // bis zur ListCell „hochklettern“
            while (node != null && node != tagList && !(node instanceof javafx.scene.control.ListCell)) {
                node = node.getParent();
            }
            if (node instanceof javafx.scene.control.ListCell<?> cell) {
                e.consume(); // eigenes Toggle-Handling übernehmen
                int index = cell.getIndex();
                if (index >= 0) {
                    var sm = tagList.getSelectionModel();
                    if (sm.getSelectedIndices().contains(index)) {
                        sm.clearSelection(index);      // abwählen
                    } else {
                        sm.select(index);              // auswählen
                    }
                }
                applyActiveFilters();
            }
        });

        // hübsche Reminder-Zellen mit Buttons
        setupReminderCells();
        refreshReminders();

        // alle 60s refresher (und 1x direkt)
        var timeline = new Timeline(
                new KeyFrame(Duration.seconds(0), e -> refreshReminders()),
                new KeyFrame(Duration.seconds(15))
        );
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }

    @FXML
    public void onNewIdea() {
        try {
            var url = MainApp.class.getResource("/de/kassel/ui/NewIdeaDialog.fxml");
            var loader = new FXMLLoader(Objects.requireNonNull(url));
            Parent content = loader.load();

            var ctrl = loader.getController();
            if (ctrl instanceof NewIdeaDialogController dlg) {
                // Buttons programmatisch hinzufügen (FXML enthält keine)
                var dialog = new Dialog<de.kassel.model.Idea>();
                dialog.setTitle("Neue Idee");
                dialog.getDialogPane().setContent(content);
                dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

                var okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
                dlg.bindOkDisable(okBtn);

                dialog.setResultConverter(bt -> bt == ButtonType.OK ? dlg.buildIdeaOrShowError() : null);

                var result = dialog.showAndWait();
                if (result.isPresent() && result.get() != null) {
                    var idea = result.get();

                    // 1) Idee speichern → neue ID holen
                    var ideaRepo = new de.kassel.db.IdeaRepository();
                    long newId = ideaRepo.insert(idea);

// 2) Tags übernehmen
                    var selectedTags = dlg.getSelectedTagNames();
                    new de.kassel.db.TagRepository().replaceIdeaTags(newId, selectedTags);

// 3) Reminder (optional)
                    var when = dlg.getReminderEpochOrNull();
                    if (when != null) {
                        new de.kassel.db.ReminderRepository()
                                .upsertForIdea(newId, when, /* note: */ idea.title());
                    }

// 4) NEUE Attachments aus dem Dialog übernehmen
                    var atRepo = new de.kassel.db.AttachmentRepository();
                    for (var p : dlg.getNewAttachmentPaths()) {
                        atRepo.insertFromPath(newId, p);
                    }


                    // 4) UI-Feedback + Refresh (Status-Ansicht beibehalten)
                    statusLabel.setText("Idee gespeichert: " + idea.title());
                    var currentNav = navList.getSelectionModel().getSelectedItem();
                    loadIdeaListForStatus(NAV_TO_STATUS.getOrDefault(currentNav, "all"));
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            statusLabel.setText("Fehler beim Öffnen des Neu-Dialogs!");
        }
    }

    @FXML
    private void onSearch() {
        applyActiveFilters();
        String q = searchField.getText();
        statusLabel.setText(q == null || q.isBlank() ? "Suche: (leer)" : "Suche: " + q);
    }

    @FXML
    private void onOpenSettings(javafx.event.ActionEvent e) {
        try {
            var url = MainApp.class.getResource("/de/kassel/ui/SettingsDialog.fxml");
            var loader = new javafx.fxml.FXMLLoader(java.util.Objects.requireNonNull(url));
            DialogPane pane = loader.load(); // Root ist DialogPane

            var ctrl = loader.getController();
            if (ctrl instanceof SettingsDialogController dlg) {
                // aktuelle Settings rein
                var current = (settings == null) ? de.kassel.settings.AppSettings.defaults() : settings;
                dlg.setInitial(current);

                var dialog = new Dialog<de.kassel.settings.AppSettings>();
                dialog.setTitle("Einstellungen");
                dialog.setDialogPane(pane);
                dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

                dialog.setResultConverter(bt -> (bt == ButtonType.OK)
                        ? dlg.buildOrShowError(current)
                        : null);

                var result = dialog.showAndWait();
                if (result.isPresent() && result.get() != null) {
                    // speichern + anwenden
                    settings = result.get();
                    de.kassel.settings.SettingsStore.save(settings);
                    setupReminderRefreshTimer(settings.reminderRefreshSeconds());
                    statusLabel.setText("Einstellungen gespeichert.");
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            statusLabel.setText("Fehler beim Öffnen der Einstellungen!");
        }
    }


    /** Wendet aktuellen Suchtext + gewählte Tags auf die geladene Liste an. */
    private void applyActiveFilters() {
        if (currentList == null) return;

        String q = (searchField.getText() == null) ? "" : searchField.getText().trim();
        var selectedTags = new java.util.ArrayList<>(tagList.getSelectionModel().getSelectedItems());

        currentList.applyFilter(q, selectedTags);
    }

    private void loadKanban() {
        try {
            var url = MainApp.class.getResource("/de/kassel/ui/KanbanView.fxml");
            var loader = new FXMLLoader(Objects.requireNonNull(url));
            Parent view = loader.load();

            currentList = null; // Board nutzt die Listen-Filter nicht
            contentPane.getChildren().setAll(view);
            statusLabel.setText("Ansicht: Board");
        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Fehler beim Laden des Boards!");
        }
    }

    // -------- Reminder UI --------

    /** Anzeige-Datensatz für das Reminder-ListView. */
    public static class ReminderRow {
        public final long id;
        public final long ideaId;
        public final String title;
        public final long dueAt; // epoch secs
        public ReminderRow(long id, long ideaId, String title, long dueAt) {
            this.id = id; this.ideaId = ideaId; this.title = title; this.dueAt = dueAt;
        }
    }

    private String formatDue(long epochSeconds) {
        var zdt = Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault());
        var fmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                .withLocale(Locale.getDefault());
        return zdt.format(fmt);
    }

    private void setupReminderCells() {
        reminderList.setCellFactory(lv -> new ListCell<>() {
            private final Label titleLbl = new Label();
            private final Label dueLbl   = new Label();
            private final Button openBtn   = new Button("Öffnen");
            private final Button snoozeBtn = new Button("Snooze");
            private final Button doneBtn   = new Button("Erledigt");
            private final Region spacer = new Region();
            private final HBox box = new HBox(10);

            {
                // „Zwei Spalten“-Gefühl: Titel links wächst, Fälligkeitsdatum rechts,
                // danach die Buttons.
                HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
                // optional leichtes Styling
                dueLbl.setStyle("-fx-opacity:0.8;");

                box.getChildren().addAll(titleLbl, spacer, dueLbl, openBtn, snoozeBtn, doneBtn);
            }

            @Override protected void updateItem(ReminderRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                titleLbl.setText(item.title);
                // Fälligkeitsdatum formatiert + farblich markieren
                long now = System.currentTimeMillis() / 1000L;
                long diff = item.dueAt - now; // Sekunden-Differenz

                dueLbl.setText(formatDue(item.dueAt));

                if (diff < 0) {
                    // überfällig
                    dueLbl.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                } else if (diff < 24 * 3600) {
                    // innerhalb des nächten Tages
                    dueLbl.setStyle("-fx-text-fill: darkorange; -fx-font-weight: bold;");
                } else {
                    // normal
                    dueLbl.setStyle("-fx-text-fill: -fx-text-inner-color; -fx-opacity: 0.8;");
                }
                setText(null);
                setGraphic(box);

                openBtn.setOnAction(e -> {
                    navList.getSelectionModel().select("Alle Ideen");
                    loadIdeaListForStatus("all");
                    statusLabel.setText("Geöffnet: " + item.title);
                });

                snoozeBtn.setOnAction(e -> {
                    new de.kassel.db.ReminderRepository().snooze(item.id, 10);
                    refreshReminders();
                });

                doneBtn.setOnAction(e -> {
                    new de.kassel.db.ReminderRepository().markDone(item.id);
                    refreshReminders();
                });
            }
        });
    }


    private void refreshReminders() {
        long now = System.currentTimeMillis() / 1000L;
        long horizon = 3* 24 * 60 * 60; // 30 Minuten Vorschau

        var repo = new de.kassel.db.ReminderRepository();
        var due = repo.findDueOrUpcoming(now, horizon); // List<Reminder>

        // Titel der Idea nachladen
        var ideaRepo = new de.kassel.db.IdeaRepository();
        var rows = new java.util.ArrayList<ReminderRow>();

        for (var r : due) {
            var optIdea = ideaRepo.findById(r.ideaId());
            optIdea.ifPresent(idea ->
                    rows.add(new ReminderRow(r.id(), r.ideaId(), idea.title(), r.dueAt())));
        }

        reminderList.getItems().setAll(rows);
        reminderList.getItems().sort(Comparator.comparingLong(r -> r.dueAt));

    }

    private void setupReminderRefreshTimer(int seconds) {
        if (reminderTimeline != null) {
            reminderTimeline.stop();
        }
        reminderTimeline = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(0), e -> refreshReminders()),
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(Math.max(5, seconds)))
        );
        reminderTimeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
        reminderTimeline.play();
    }

    private String resolveStartViewFromSettings() {
        // 1) settings.properties laden
        var settings = SettingsStore.load();        // -> AppSettings

        // 2) Wert holen (kann z. B. "Inbox", "Board" ODER "inbox", "all" sein)
        String raw = settings.startupView();
        if (raw == null || raw.isBlank()) {
            return "Alle Ideen";
        }

        // 3) Wenn es bereits exakt ein Label in der Nav ist, direkt verwenden
        if (navList.getItems().contains(raw)) {
            return raw;
        }

        // 4) Sonst so behandeln, als wäre es ein Status-Key -> ins Label zurück-mapen
        return NAV_TO_STATUS.entrySet().stream()
                .filter(e -> e.getValue().equalsIgnoreCase(raw)) // z. B. "inbox" -> "Inbox"
                .map(java.util.Map.Entry::getKey)
                .findFirst()
                .orElse("Alle Ideen");
    }



}
