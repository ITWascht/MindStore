package de.kassel.ui;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import de.kassel.db.IdeaRepository;
import de.kassel.model.Idea;
import javafx.collections.FXCollections;

public class IdeaListController {
    @FXML private TextField filterField;
    @FXML private TableView<IdeaRow> table;
    @FXML private TableColumn<IdeaRow, String> colTitle;
    @FXML private TableColumn<IdeaRow, Integer> colPriority;
    @FXML private TableColumn<IdeaRow, String> colStatus;
    private final IdeaRepository repo = new IdeaRepository();
    private String statusFilter = null;

    @FXML
    public void initialize(){
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colPriority.setCellValueFactory(new PropertyValueFactory<>("priority"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        reload();

//        table.setItems(FXCollections.observableArrayList(
//                new IdeaRow("SQLite integrieren",1,"inbox"),
//                new IdeaRow("FXML laden",2,"doing"),
//                new IdeaRow("UI-Theming",3,"draft")
//        ));
    }

    public void setStatusFilter(String status) {
        this.statusFilter = status;
        reload();
    }

    private void reload() {
        var ideas = (statusFilter == null || statusFilter.equals("all"))
                ? repo.findAll()
                : repo.findByStatus(statusFilter);

        // Map auf Table-DTO (du kannst auch direkt Idea verwenden und PropertyValueFactory anpassen)
        var rows = FXCollections.<IdeaRow>observableArrayList();
        for (Idea i : ideas) {
            rows.add(new IdeaRow(i.getTitle(), i.getPriority(), i.getStatus()));
        }
        table.setItems(rows);
    }

    private void applyStatusFilter(){
        if (statusFilter == null || statusFilter.equals("all")) return;
        table.getItems().removeIf(r-> !r.getStatus().equalsIgnoreCase(statusFilter));
    }

    @FXML
    private void onApplyFilter(){
        //erstmal Dummy
    table.getItems().removeIf(r->
            filterField.getText()!=null && !filterField.getText().isBlank() && !r.getTitle().toLowerCase().contains(filterField.getText().toLowerCase())
    );
    }

    public static class IdeaRow{
        private final String title;
        private final Integer priority;
        private final String status;
        public IdeaRow(String title, Integer priority,String status){
            this.title = title;
            this.priority = priority;
            this.status = status;
        }
        public String getTitle() {return title;}
        public Integer getPriority() {return priority;}
        public String getStatus() {return status;}
    }
}
