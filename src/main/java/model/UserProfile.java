package model;

/**
 * UserProfile holds the local user's identity and UI preferences.
 * Only one profile exists per installation (id = 1 in the DB).
 */
public class UserProfile {

    private String name;
    private String email;
    private String avatar;   // a single emoji, e.g. "👤" or "🦊"
    private String motto;
    private String theme;    // "dark" or "light"

    public UserProfile() {
        this("", "", "👤", "", "dark");
    }

    public UserProfile(String name, String email, String avatar, String motto, String theme) {
        this.name   = name   == null ? "" : name;
        this.email  = email  == null ? "" : email;
        this.avatar = (avatar == null || avatar.isBlank()) ? "👤" : avatar;
        this.motto  = motto  == null ? "" : motto;
        this.theme  = (theme == null || theme.isBlank()) ? "dark" : theme;
    }

    public String getName()   { return name; }
    public void setName(String name)     { this.name = name == null ? "" : name; }

    public String getEmail()  { return email; }
    public void setEmail(String email)   { this.email = email == null ? "" : email; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) {
        this.avatar = (avatar == null || avatar.isBlank()) ? "👤" : avatar;
    }

    public String getMotto()  { return motto; }
    public void setMotto(String motto)   { this.motto = motto == null ? "" : motto; }

    public String getTheme()  { return theme; }
    public void setTheme(String theme)   {
        this.theme = "light".equalsIgnoreCase(theme) ? "light" : "dark";
    }

    public boolean isDark() { return !"light".equalsIgnoreCase(theme); }

    /** Display label for the header button. */
    public String getDisplayLabel() {
        String n = name.isBlank() ? "User" : name;
        return "[" + avatar + " " + n + "]";
    }
}