package model;

/**
 * Week 1: Enum representing the user's mood before a session.
 * Using an enum keeps the values fixed and prevents typos like "hapy" vs "happy".
 */
public enum Mood {
    HAPPY("😀", "Happy"),
    CONTENT("🙂", "Content"),
    NEUTRAL("😐", "Neutral"),
    TIRED("😕", "Tired"),
    SAD("😞", "Sad");

    private final String emoji;
    private final String label;

    Mood(String emoji, String label) {
        this.emoji = emoji;
        this.label = label;
    }

    public String getEmoji() { return emoji; }
    public String getLabel() { return label; }

    /** Reverse lookup from emoji character. */
    public static Mood fromEmoji(String e) {
        for (Mood m : values()) {
            if (m.emoji.equals(e)) return m;
        }
        return NEUTRAL;
    }
}