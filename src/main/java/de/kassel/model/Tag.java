package de.kassel.model;

public record Tag(
        long id,
        String name,
        String color,     // nullable hex z.B. "#8BC34A"
        long createdAt
) {
    // Komfort-Konstruktor für Inserts
    public Tag(String name, String color) {
        this(0L, name, color, 0L);
    }
}
