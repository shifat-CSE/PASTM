package model;

public class Preset {

    private String activity;
    private String category;
    private int usageCount;

    public Preset(String activity, String category, int usageCount) {
        this.activity = activity;
        this.category = category;
        this.usageCount = usageCount;
    }

    public String getActivity() { return activity; }
    public void setActivity(String activity) { this.activity = activity; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public int getUsageCount() { return usageCount; }
    public void setUsageCount(int usageCount) { this.usageCount = usageCount; }

    public void incrementUsage() { this.usageCount++; }

    public String getDisplayText() { return "[" + activity + "]"; }

    @Override
    public String toString() { return getDisplayText() + " ×" + usageCount; }
}