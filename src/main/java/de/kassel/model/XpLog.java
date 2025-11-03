package de.kassel.model;

public record XpLog(
        long id,
        long ideaId,
        int deltaXp,       // +/-
        String reason,     // optional
        long createdAt
) {
    public XpLog(long ideaId, int deltaXp, String reason) {
        this(0L, ideaId, deltaXp, reason, 0L);
    }
}
