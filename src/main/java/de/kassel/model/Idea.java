package de.kassel.model;

public record Idea(
        long id,
        String title,
        String body,
        Priority priority,
        IdeaStatus status,
        Integer effortMinutes,     // nullable
        long createdAt,            // epoch seconds
        Long updatedAt             // nullable epoch seconds
) {
    /** Hilfs-Ctor fürs Einfügen (id/createdAt vom DB-Default) */
    public Idea(String title, String body, Priority priority, IdeaStatus status, Integer effortMinutes) {
        this(0L, title, body, priority, status, effortMinutes, 0L, null);
    }
}