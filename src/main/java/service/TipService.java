package service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.QuoteListResponse;
import model.Tip;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * TipService demonstrates every JSON technique from the tutorial:
 *   §7  parse from a file on disk / classpath
 *   §8  parse from a raw String
 *   §9  parse an array of objects
 *   §10 serialize Java objects back to JSON files
 *   §11 fetch and parse JSON from a real HTTP API
 *   §13 parse into a Map for dynamic structures
 *   §14 handle unknown fields gracefully
 */
public class TipService {

    private static final String RANDOM_URL    = "https://dummyjson.com/quotes/random";
    private static final String LIST_URL      = "https://dummyjson.com/quotes?limit=20";
    private static final String FALLBACK_RES  = "fallback_tips.json";
    private static final String CACHE_FILE    = "tips_cache.json";

    private final HttpClient   client;
    private final ObjectMapper mapper;
    private final Random       random = new Random();

    public TipService() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        this.mapper = new ObjectMapper()
                // §14: never crash on extra / unknown JSON fields
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /* =================== PUBLIC API =================== */

    /** Fetch one random tip from the live API; falls back to bundled file if offline. */
    public Tip fetchRandomTip() {
        try {
            String json = httpGet(RANDOM_URL);
            Tip tip = mapper.readValue(json, Tip.class);   // §11 + §8
            saveTipToCache(tip);                            // §10 (write back)
            return tip;
        } catch (Exception e) {
            System.err.println("[TipService] API failed, using fallback: " + e.getMessage());
            return randomFallbackTip();
        }
    }

    /** Fetch an array of tips from the API — used to demonstrate §9. */
    public List<Tip> fetchTipsList() throws Exception {
        String json = httpGet(LIST_URL);
        QuoteListResponse response = mapper.readValue(json, QuoteListResponse.class);
        return response.getResults();
    }

    /* =================== MANUAL SECTION DEMOS =================== */

    /** §8 — Parse JSON straight from a String. */
    public Tip parseFromString(String json) throws Exception {
        return mapper.readValue(json, Tip.class);
    }

    /** §7 — Load a Tip from a file on disk. */
    public Tip loadFromFile(File file) throws Exception {
        return mapper.readValue(file, Tip.class);
    }

    /** §13 — Parse dynamic JSON into a Map<String,Object>. */
    public Map<String, Object> parseToMap(String json) throws Exception {
        return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    }

    /** §10 — Write a Tip back to a JSON file (pretty printed). */
    public void writeToFile(Tip tip, File file) throws Exception {
        mapper.writerWithDefaultPrettyPrinter().writeValue(file, tip);
    }

    /** §10 + §9 — Write a list of Tips to a JSON file. */
    public void writeListToFile(List<Tip> tips, File file) throws Exception {
        mapper.writerWithDefaultPrettyPrinter().writeValue(file, tips);
    }

    /* =================== INTERNALS =================== */

    /** §7 — load the bundled fallback list from src/main/resources. */
    private List<Tip> loadFallbackTips() {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream(FALLBACK_RES)) {
            if (in == null) return List.of();
            QuoteListResponse r = mapper.readValue(in, QuoteListResponse.class);
            return r.getResults() == null ? List.of() : r.getResults();
        } catch (Exception e) {
            System.err.println("[TipService] fallback load failed: " + e.getMessage());
            return List.of();
        }
    }

    private Tip randomFallbackTip() {
        List<Tip> tips = loadFallbackTips();
        if (tips.isEmpty())
            return new Tip("Discipline equals freedom.", "Jocko Willink");
        return tips.get(random.nextInt(tips.size()));
    }

    private void saveTipToCache(Tip tip) {
        try {
            writeToFile(tip, new File(CACHE_FILE));   // §10
        } catch (Exception e) {
            System.err.println("[TipService] cache save failed: " + e.getMessage());
        }
    }

    /** §11 — Java 11 HttpClient GET, returns raw JSON body as String. */
    private String httpGet(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200)
            throw new RuntimeException("HTTP " + response.statusCode());
        return response.body();
    }
}