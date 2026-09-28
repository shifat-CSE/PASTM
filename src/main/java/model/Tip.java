package model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Tip represents a single motivational quote returned by the API:
 *   https://dummyjson.com/quotes/random
 *
 * Example JSON:
 * {
 *   "id": 1,
 *   "quote": "Your heart is the size of an ocean...",
 *   "author": "Rumi"
 * }
 *
 * We map JSON key "quote" -> Java field "content",
 * and ignore any extra fields we don't need (manual §14).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Tip {

    private int id;

    @JsonProperty("quote")
    private String content;

    private String author;

    /** Default constructor required by Jackson (manual §6). */
    public Tip() {}

    /** Optional convenience constructor for manual creation. */
    public Tip(String content, String author) {
        this.content = content;
        this.author  = author;
    }

    public int getId()                  { return id; }
    public void setId(int id)           { this.id = id; }

    public String getContent()          { return content; }
    public void   setContent(String c)  { this.content = c; }

    public String getAuthor()           { return author; }
    public void   setAuthor(String a)   { this.author = a; }

    @Override
    public String toString() {
        return "\"" + content + "\" — " + author;
    }
}