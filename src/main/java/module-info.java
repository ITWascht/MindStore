module de.kassel.MindStore {
    requires java.sql;
    requires javafx.controls;
    requires javafx.fxml;
    exports de.kassel.ui;
    opens de.kassel.ui to javafx.fxml;
}