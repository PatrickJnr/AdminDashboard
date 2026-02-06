package uk.co.grimtech.admin.web;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import uk.co.grimtech.admin.CustomLogger;
import uk.co.grimtech.admin.AdminDashboardPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class CurseForgeAPI {
    private static final String API_KEY = "$2a$10$VJ6wYwy6mFhRg3EB7NabEuD/vGWm4QStItRBOBw4uy5tTaSUMnCF2";
    private static final String BASE_URL = "https://api.curseforge.com/v1";
    private static final int HYTALE_GAME_ID = 70216; // Hytale's game ID on CurseForge
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    
    // Cache for mod metadata with timestamp
    private static final Map<String, CachedMod> modCache = new HashMap<>();
    private static final long CACHE_DURATION_MS = 15 * 60 * 1000; // 15 minutes
    
    private static class CachedMod {
        JsonObject data;
        long timestamp;
        
        CachedMod(JsonObject data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_DURATION_MS;
        }
    }
    
    private static CustomLogger getLogger() {
        return AdminDashboardPlugin.getCustomLogger();
    }
    
    /**
     * Search for a mod by name on CurseForge
     */
    public static CompletableFuture<JsonObject> searchMod(String modName) {
        // Check cache first
        String cacheKey = modName.toLowerCase();
        CachedMod cached = modCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return CompletableFuture.completedFuture(cached.data);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Clean up mod name for search
                String searchQuery = modName
                    .replace(":", " ")
                    .replace("_", " ")
                    .replaceAll("([a-z])([A-Z])", "$1 $2") // Add space before capital letters (CamelCase)
                    .trim();
                
                String url = BASE_URL + "/mods/search?gameId=" + HYTALE_GAME_ID + 
                            "&searchFilter=" + java.net.URLEncoder.encode(searchQuery, "UTF-8") +
                            "&pageSize=5"; // Get top 5 results for better matching
                
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Accept", "application/json")
                        .header("x-api-key", API_KEY)
                        .header("User-Agent", "Hytale-Admin-Dashboard/1.0")
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();
                
                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    JsonObject result = GSON.fromJson(response.body(), JsonObject.class);
                    
                    // Validate response structure
                    if (!result.has("data")) {
                        getLogger().warning("[CurseForge] Invalid response structure for: " + modName);
                        return null;
                    }
                    
                    JsonArray data = result.getAsJsonArray("data");
                    
                    if (data != null && data.size() > 0) {
                        // Try to find best match
                        JsonObject bestMatch = null;
                        int bestScore = 0;
                        
                        for (int i = 0; i < data.size(); i++) {
                            JsonObject mod = data.get(i).getAsJsonObject();
                            String cfName = mod.get("name").getAsString();
                            
                            // Normalize both names for comparison (remove spaces, lowercase)
                            String normalizedCfName = cfName.replaceAll("\\s+", "").toLowerCase();
                            String normalizedModName = modName.replaceAll("\\s+", "").toLowerCase();
                            String normalizedSearchQuery = searchQuery.replaceAll("\\s+", "").toLowerCase();
                            
                            // Scoring: exact match > normalized match > contains > starts with
                            int score = 0;
                            if (cfName.equalsIgnoreCase(modName) || cfName.equalsIgnoreCase(searchQuery)) {
                                score = 100; // Exact match with original or search query
                            } else if (normalizedCfName.equals(normalizedModName) || normalizedCfName.equals(normalizedSearchQuery)) {
                                score = 95; // Normalized match (ignoring spaces)
                            } else if (cfName.toLowerCase().contains(modName.toLowerCase()) || 
                                       cfName.toLowerCase().contains(searchQuery.toLowerCase())) {
                                score = 50; // Contains original or search query
                            } else if (normalizedCfName.contains(normalizedModName) || 
                                       normalizedCfName.contains(normalizedSearchQuery)) {
                                score = 45; // Contains normalized
                            } else if (modName.toLowerCase().contains(cfName.toLowerCase()) || 
                                       searchQuery.toLowerCase().contains(cfName.toLowerCase())) {
                                score = 30; // Reverse contains
                            }
                            
                            if (score > bestScore) {
                                bestScore = score;
                                bestMatch = mod;
                            }
                        }
                        
                        if (bestMatch == null) {
                            bestMatch = data.get(0).getAsJsonObject(); // Fallback to first result
                        }
                        
                        // Extract relevant info
                        JsonObject modInfo = new JsonObject();
                        modInfo.addProperty("id", bestMatch.get("id").getAsInt());
                        modInfo.addProperty("name", bestMatch.get("name").getAsString());
                        modInfo.addProperty("slug", bestMatch.get("slug").getAsString());
                        
                        // Get latest file version
                        if (bestMatch.has("latestFiles") && bestMatch.getAsJsonArray("latestFiles").size() > 0) {
                            JsonObject latestFile = bestMatch.getAsJsonArray("latestFiles").get(0).getAsJsonObject();
                            modInfo.addProperty("version", latestFile.get("displayName").getAsString());
                        }
                        
                        // Get icon/logo
                        if (bestMatch.has("logo")) {
                            JsonObject logo = bestMatch.getAsJsonObject("logo");
                            modInfo.addProperty("iconUrl", logo.get("thumbnailUrl").getAsString());
                        }
                        
                        // Get author
                        if (bestMatch.has("authors") && bestMatch.getAsJsonArray("authors").size() > 0) {
                            JsonObject author = bestMatch.getAsJsonArray("authors").get(0).getAsJsonObject();
                            modInfo.addProperty("author", author.get("name").getAsString());
                        }
                        
                        // Get download count
                        if (bestMatch.has("downloadCount")) {
                            modInfo.addProperty("downloads", bestMatch.get("downloadCount").getAsLong());
                        }
                        
                        // Get CurseForge URL
                        if (bestMatch.has("links")) {
                            JsonObject links = bestMatch.getAsJsonObject("links");
                            if (links.has("websiteUrl")) {
                                modInfo.addProperty("url", links.get("websiteUrl").getAsString());
                            }
                        }
                        
                        // Cache the result
                        modCache.put(cacheKey, new CachedMod(modInfo));
                        
                        getLogger().info("[CurseForge] Found mod: " + bestMatch.get("name").getAsString() + " (score: " + bestScore + ")");
                        return modInfo;
                    }
                } else if (response.statusCode() == 401) {
                    getLogger().severe("[CurseForge] Unauthorized - API key may be invalid");
                } else if (response.statusCode() == 403) {
                    getLogger().severe("[CurseForge] Forbidden - API key may lack Hytale permissions");
                } else if (response.statusCode() == 429) {
                    getLogger().warning("[CurseForge] Rate limit exceeded");
                } else {
                    getLogger().warning("[CurseForge] HTTP " + response.statusCode() + " for: " + modName);
                }
                
                return null;
            } catch (Exception e) {
                getLogger().log("ERROR", "[CurseForge] Error searching for mod: " + modName, e);
                return null;
            }
        });
    }
    
    /**
     * Clear the cache (useful for testing)
     */
    public static void clearCache() {
        modCache.clear();
        getLogger().info("[CurseForge] Cache cleared");
    }
}
