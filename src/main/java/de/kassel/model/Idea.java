package de.kassel.model;

public class Idea {
    private final long id;
    private final String title;
    private final String body;
    private final int priority;
    private final String status;

    public Idea(long id, String title, String body, int priority, String status) {
        this.id = id; this.title = title; this.body = body; this.priority = priority; this.status = status;
    }
    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public int getPriority() { return priority; }
    public String getStatus() { return status; }
}
