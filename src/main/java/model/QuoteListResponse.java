package model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Wrapper POJO for API responses shaped like:
 *   { "results": [ { "content": "...", "author": "..." }, ... ] }
 *
 * Used both for the live API list endpoint and the fallback_tips.json file.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class QuoteListResponse {

    private List<Tip> results;

    public QuoteListResponse() {}

    public List<Tip> getResults()              { return results; }
    public void      setResults(List<Tip> res) { this.results = res; }
}