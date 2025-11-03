module de.kassel.mindstore {
    requires java.sql;
    requires javafx.controls;
    requires javafx.fxml;
    requires java.management;

    opens de.kassel.ui to javafx.fxml; // FXML-Controller
    exports de.kassel.ui;

    exports de.kassel.model; // falls außerhalb verwendet
}
