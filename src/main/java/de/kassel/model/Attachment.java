package de.kassel.model;

public record Attachment(
        long id,
        long ideaId,
        String fileName,
        String filePath,
        String mimeType,     // nullable
        Long sizeBytes,      // nullable
        long createdAt
) {
    public Attachment(long ideaId, String fileName, String filePath, String mimeType, Long sizeBytes) {
        this(0L, ideaId, fileName, filePath, mimeType, sizeBytes, 0L);
    }
}
