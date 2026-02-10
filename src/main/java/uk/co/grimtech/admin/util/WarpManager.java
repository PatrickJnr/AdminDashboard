package uk.co.grimtech.admin.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import uk.co.grimtech.admin.CustomLogger;
import uk.co.grimtech.admin.AdminWebDashPlugin;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WarpManager {
    private static final Gson GSON = new Gson();
    private static final Path WARPS_FILE = Paths.get("warps.json");
    private static final Map<String, Warp> warps = new ConcurrentHashMap<>();
    
    private static CustomLogger getLogger() {
        return AdminWebDashPlugin.getCustomLogger();
    }
    
    public static class Warp {
        public String name;
        public String world;
        public double x;
        public double y;
        public double z;
        public UUID createdBy;
        public long createdAt;
        
        public Warp() {}
        
        public Warp(String name, String world, double x, double y, double z, UUID createdBy) {
            this.name = name;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.createdBy = createdBy;
            this.createdAt = Instant.now().toEpochMilli();
        }
    }
    
    public static void load() {
        if (Files.exists(WARPS_FILE)) {
            try {
                String json = Files.readString(WARPS_FILE);
                Map<String, Warp> loaded = GSON.fromJson(json, 
                    new TypeToken<Map<String, Warp>>(){}.getType());
                if (loaded != null) {
                    warps.putAll(loaded);
                    getLogger().info("[WarpManager] Loaded " + warps.size() + " warps");
                }
            } catch (Exception e) {
                getLogger().log("ERROR", "[WarpManager] Failed to load warps", e);
            }
        }
    }
    
    public static void save() {
        try {
            Files.writeString(WARPS_FILE, GSON.toJson(warps));
        } catch (Exception e) {
            getLogger().log("ERROR", "[WarpManager] Failed to save warps", e);
        }
    }
    
    public static void createWarp(String name, String world, double x, double y, double z, UUID createdBy) {
        Warp warp = new Warp(name, world, x, y, z, createdBy);
        warps.put(name.toLowerCase(), warp);
        save();
        getLogger().info("[WarpManager] Created warp: " + name);
    }
    
    public static void deleteWarp(String name) {
        warps.remove(name.toLowerCase());
        save();
        getLogger().info("[WarpManager] Deleted warp: " + name);
    }
    
    public static Warp getWarp(String name) {
        return warps.get(name.toLowerCase());
    }
    
    public static Map<String, Warp> getWarps() {
        return new HashMap<>(warps);
    }
    
    public static boolean exists(String name) {
        return warps.containsKey(name.toLowerCase());
    }
}
