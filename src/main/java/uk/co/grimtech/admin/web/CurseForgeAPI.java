package uk.co.grimtech.admin.web;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import uk.co.grimtech.admin.CustomLogger;
import uk.co.grimtech.admin.AdminWebDashPlugin;

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
    private static final int HYTALE_GAME_ID = 70216; 
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    
    
    private static final Map<String, CachedMod> modCache = new HashMap<>();
    private static final long CACHE_DURATION_MS = 15 * 60 * 1000; 
    
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
        return AdminWebDashPlugin.getCustomLogger();
    }
    
    
    public static CompletableFuture<JsonObject> searchMod(String modName) {
        
        String cacheKey = modName.toLowerCase();
        CachedMod cached = modCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            getLogger().info("[CurseForge] Using cached result for: " + modName);
            return CompletableFuture.completedFuture(cached.data);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                getLogger().info("[CurseForge] Searching for mod: " + modName);
                
                
                JsonObject result = searchCurseForge(modName, modName);
                if (result != null) {
                    getLogger().info("[CurseForge] Found match for " + modName + " (original): " + result.toString());
                    modCache.put(cacheKey, new CachedMod(result));
                    return result;
                }
                
                
                String searchQuery = modName
                    .replace(":", " ")
                    .replace("_", " ")
                    .replaceAll("([a-z])([A-Z])", "$1 $2") 
                    .trim();
                
                if (!searchQuery.equals(modName)) {
                    getLogger().info("[CurseForge] Search query: " + searchQuery);
                    result = searchCurseForge(searchQuery, modName);
                    if (result != null) {
                        getLogger().info("[CurseForge] Found match for " + modName + ": " + result.toString());
                        modCache.put(cacheKey, new CachedMod(result));
                        return result;
                    }
                }
                
                
                if (searchQuery.contains(" ")) {
                    String noSpaceQuery = searchQuery.replace(" ", "");
                    if (!noSpaceQuery.equals(modName)) {
                        getLogger().info("[CurseForge] Trying no-space search: " + noSpaceQuery);
                        result = searchCurseForge(noSpaceQuery, modName);
                        if (result != null) {
                            getLogger().info("[CurseForge] Found match for " + modName + ": " + result.toString());
                            modCache.put(cacheKey, new CachedMod(result));
                            return result;
                        }
                    }
                }
                
                
                String[] wordsToRemove = {"Tree", "Block", "Item", "Entity", "Mod"};
                for (String word : wordsToRemove) {
                    String simplifiedQuery = searchQuery.replaceAll("\\b" + word + "\\b", "").replaceAll("\\s+", " ").trim();
                    if (!simplifiedQuery.isEmpty() && !simplifiedQuery.equals(searchQuery)) {
                        getLogger().info("[CurseForge] Trying simplified search: " + simplifiedQuery);
                        result = searchCurseForge(simplifiedQuery, modName);
                        if (result != null) {
                            modCache.put(cacheKey, new CachedMod(result));
                            return result;
                        }
                    }
                }
                
                getLogger().warning("[CurseForge] No match found for: " + modName);
                return null;
            } catch (Exception e) {
                getLogger().log("ERROR", "[CurseForge] Error searching for mod: " + modName, e);
                return null;
            }
        });
    }
    
    
    private static JsonObject searchCurseForge(String searchQuery, String originalModName) {
        try {
            String url = BASE_URL + "/mods/search?gameId=" + HYTALE_GAME_ID + 
                        "&searchFilter=" + java.net.URLEncoder.encode(searchQuery, "UTF-8") +
                        "&pageSize=5"; 
            
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
                
                
                if (!result.has("data")) {
                    getLogger().warning("[CurseForge] Invalid response structure for: " + searchQuery);
                    return null;
                }
                
                JsonArray data = result.getAsJsonArray("data");
                
                if (data != null && data.size() > 0) {
                    
                    JsonObject bestMatch = null;
                    int bestScore = 0;
                    
                    for (int i = 0; i < data.size(); i++) {
                        JsonObject mod = data.get(i).getAsJsonObject();
                        String cfName = mod.get("name").getAsString();
                        String cfSlug = mod.get("slug").getAsString();
                        
                        
                        String normalizedCfName = cfName.replaceAll("\\s+", "").toLowerCase();
                        String normalizedCfSlug = cfSlug.replaceAll("[\\s-_]+", "").toLowerCase();
                        String normalizedModName = originalModName.replaceAll("[\\s-_:]+", "").toLowerCase();
                        String normalizedSearchQuery = searchQuery.replaceAll("[\\s-_]+", "").toLowerCase();
                        
                        
                        int score = 0;
                        
                        
                        if (cfSlug.equalsIgnoreCase(originalModName) || normalizedCfSlug.equals(normalizedModName)) {
                            score = 1000; 
                        }
                        
                        else if (cfName.equalsIgnoreCase(originalModName) || normalizedCfName.equals(normalizedModName)) {
                            score = 500; 
                        }
                        
                        else if (cfName.equalsIgnoreCase(searchQuery) || normalizedCfSlug.equals(normalizedSearchQuery)) {
                            score = 400; 
                        }
                        else if (normalizedCfName.equals(normalizedSearchQuery)) {
                            score = 350; 
                        }
                        
                        else if (normalizedCfSlug.contains(normalizedModName) && normalizedModName.length() >= 5) {
                            
                            int lengthDiff = Math.abs(normalizedCfSlug.length() - normalizedModName.length());
                            score = 200 - (lengthDiff * 10); 
                        }
                        else if (normalizedCfName.contains(normalizedModName) && normalizedModName.length() >= 5) {
                            int lengthDiff = Math.abs(normalizedCfName.length() - normalizedModName.length());
                            score = 180 - (lengthDiff * 10);
                        }
                        
                        else if (normalizedCfSlug.startsWith(normalizedModName) || normalizedCfName.startsWith(normalizedModName)) {
                            score = 70; 
                        } else if (cfName.toLowerCase().contains(originalModName.toLowerCase()) || 
                                   cfName.toLowerCase().contains(searchQuery.toLowerCase())) {
                            score = 50; 
                        } else if (normalizedCfName.contains(normalizedModName) || 
                                   normalizedCfName.contains(normalizedSearchQuery)) {
                            score = 45; 
                        } else if (originalModName.toLowerCase().contains(cfName.toLowerCase()) || 
                                   searchQuery.toLowerCase().contains(cfName.toLowerCase())) {
                            score = 30; 
                        }
                        
                        getLogger().info("[CurseForge] Candidate: " + cfName + " (slug: " + cfSlug + ", score: " + score + ")" +
                                       " | normalized slug: " + normalizedCfSlug + " vs " + normalizedModName);
                        
                        if (score > bestScore) {
                            bestScore = score;
                            bestMatch = mod;
                        }
                    }
                    
                    if (bestMatch == null) {
                        bestMatch = data.get(0).getAsJsonObject(); 
                    }
                    
                    
                    JsonObject modInfo = new JsonObject();
                    modInfo.addProperty("id", bestMatch.get("id").getAsInt());
                    modInfo.addProperty("name", bestMatch.get("name").getAsString());
                    modInfo.addProperty("slug", bestMatch.get("slug").getAsString());
                    
                    if (bestMatch.has("latestFiles") && bestMatch.getAsJsonArray("latestFiles").size() > 0) {
                        JsonObject latestFile = bestMatch.getAsJsonArray("latestFiles").get(0).getAsJsonObject();
                        modInfo.addProperty("version", latestFile.get("displayName").getAsString());
                    }
                    
                    if (bestMatch.has("logo")) {
                        JsonObject logo = bestMatch.getAsJsonObject("logo");
                        modInfo.addProperty("iconUrl", logo.get("thumbnailUrl").getAsString());
                    }
                    
                    if (bestMatch.has("authors") && bestMatch.getAsJsonArray("authors").size() > 0) {
                        JsonObject author = bestMatch.getAsJsonArray("authors").get(0).getAsJsonObject();
                        modInfo.addProperty("author", author.get("name").getAsString());
                    }
                    
                    if (bestMatch.has("downloadCount")) {
                        modInfo.addProperty("downloads", bestMatch.get("downloadCount").getAsLong());
                    }
                    
                    if (bestMatch.has("links")) {
                        JsonObject links = bestMatch.getAsJsonObject("links");
                        if (links.has("websiteUrl")) {
                            modInfo.addProperty("url", links.get("websiteUrl").getAsString());
                        }
                    }
                    
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
                getLogger().warning("[CurseForge] HTTP " + response.statusCode() + " for: " + searchQuery);
            }
            
            return null;
        } catch (Exception e) {
            getLogger().log("ERROR", "[CurseForge] Error in searchCurseForge", e);
            return null;
        }
    }
    
    
    public static void clearCache() {
        modCache.clear();
        getLogger().info("[CurseForge] Cache cleared");
    }
}
