package model;

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

    public static Mood fromEmoji(String e) {
        for (Mood m : values()) {
            if (m.emoji.equals(e)) return m;
        }
        return NEUTRAL;
    }
}