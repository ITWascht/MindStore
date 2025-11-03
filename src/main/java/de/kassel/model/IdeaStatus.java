package de.kassel.model;

public enum IdeaStatus {
    INBOX("inbox"),
    DRAFT("draft"),
    DOING("doing"),
    DONE("done"),
    ARCHIVED("archived");

    private final String db;
    IdeaStatus(String db) { this.db = db; }
    public String db() { return db; }

    public static IdeaStatus fromDb(String s) {
        if (s == null) return INBOX;
        return switch (s.toLowerCase()) {
            case "draft" -> DRAFT;
            case "doing" -> DOING;
            case "done" -> DONE;
            case "archived" -> ARCHIVED;
            default -> INBOX;
        };
    }
}