package de.kassel.model;

import javafx.beans.property.*;

public class IdeaRow {
    private final LongProperty id = new SimpleLongProperty();
    private final StringProperty title = new SimpleStringProperty();
    private final IntegerProperty priority = new SimpleIntegerProperty();
    private final StringProperty status = new SimpleStringProperty();
    private final StringProperty tags = new SimpleStringProperty("");

    public IdeaRow(long id, String title, int priority, String status) {
        this.id.set(id);
        this.title.set(title);
        this.priority.set(priority);
        this.status.set(status);
        this.tags.set("");
    }

    public IdeaRow(long id, String title, int priority, String status, String tags) {
        this(id, title, priority, status);
        this.tags.set(tags);
    }
    // ---- Getter / Setter / Properties ----
    public long getId() { return id.get(); }
    public LongProperty idProperty() { return id; }

    public String getTitle() { return title.get(); }
    public StringProperty titleProperty() { return title; }

    public int getPriority() { return priority.get(); }
    public IntegerProperty priorityProperty() { return priority; }

    public String getStatus() { return status.get(); }
    public StringProperty statusProperty() { return status; }

    public String getTags() { return tags.get(); }
    public StringProperty tagsProperty() { return tags; }
    public void setTags(String tags) { this.tags.set(tags); }

    // optional, falls du in Zukunft editierbare Tabellen möchtest:
    public void setTitle(String title) { this.title.set(title); }
    public void setPriority(int priority) { this.priority.set(priority); }
    public void setStatus(String status) { this.status.set(status); }

    @Override
    public String toString() {
        return "[" + id.get() + "] " + title.get() + " (" + status.get() + ")";
    }
}
