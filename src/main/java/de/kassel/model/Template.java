package de.kassel.model;

public record Template(
        long id,
        String name,
        String defaultTitle,   // optional
        String defaultBody,    // optional
        Integer defaultPriority,
        String defaultStatus,  // "inbox"|"draft"|...
        long createdAt,
        Long updatedAt
) {
    public Template(String name) {
        this(0L, name, null, null, null, "inbox", 0L, null);
    }
}
