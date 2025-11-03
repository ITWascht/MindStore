package de.kassel.model;

/** 1=hoch ... 4=niedrig (wie im CHECK der DB) */
public enum Priority {
    P1(1), P2(2), P3(3), P4(4);
    private final int level;
    Priority(int level) { this.level = level; }
    public int level() { return level; }

    public static Priority fromInt(int p) {
        return switch (p) {
            case 1 -> P1; case 2 -> P2; case 3 -> P3; default -> P4;
        };
    }
}
