package de.kassel.model;

public record Reminder(
        long id,
        long ideaId,
        long dueAt,       // epoch seconds
        String note,      // nullable
        boolean isDone,
        long createdAt,
        Long updatedAt    // nullable
) {
    // Komfort-Konstruktor: alles außer Timestamps
    public Reminder(long id, long ideaId, long dueAt, String note, boolean isDone) {
        this(id, ideaId, dueAt, note, isDone, 0L, null);
    }
}