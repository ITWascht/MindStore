package de.kassel.model;

public record TemplateField(
        long id,
        long templateId,
        String key,         // z.B. "effort_minutes"
        String value,       // z.B. "25"
        long createdAt,
        Long updatedAt
) {
    public TemplateField(long templateId, String key, String value) {
        this(0L, templateId, key, value, 0L, null);
    }
}
