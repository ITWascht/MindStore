package de.kassel.ui;

import de.kassel.settings.AppSettings;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class SettingsDialogController {
    @FXML private TextField refreshSecondsField;
    @FXML private TextField snoozeMinutesField;
    @FXML private ComboBox<String> startupViewBox;
    @FXML private Label errorLabel;

    @FXML
    public void initialize() {
        startupViewBox.getItems().setAll("Inbox","Draft","Doing","Done","Archived","Alle Ideen","Papierkorb","Board");
        errorLabel.setText("");
    }

    public void setInitial(AppSettings s) {
        refreshSecondsField.setText(Integer.toString(s.reminderRefreshSeconds()));
        snoozeMinutesField.setText(Integer.toString(s.defaultSnoozeMinutes()));
        startupViewBox.getSelectionModel().select(s.startupView());
    }

    public AppSettings buildOrShowError(AppSettings fallback) {
        int refresh, snooze;
        String start = startupViewBox.getValue() == null ? fallback.startupView() : startupViewBox.getValue();

        try {
            refresh = Integer.parseInt(refreshSecondsField.getText().trim());
            if (refresh < 5 || refresh > 3600) {
                errorLabel.setText("Refresh 5..3600 Sek.");
                return null;
            }
        } catch (Exception ex) {
            errorLabel.setText("Refresh muss Zahl sein.");
            return null;
        }

        try {
            snooze = Integer.parseInt(snoozeMinutesField.getText().trim());
            if (snooze < 1 || snooze > 240) {
                errorLabel.setText("Snooze 1..240 Min.");
                return null;
            }
        } catch (Exception ex) {
            errorLabel.setText("Snooze muss Zahl sein.");
            return null;
        }

        errorLabel.setText("");
        return new AppSettings(refresh, snooze, start);
    }
}
