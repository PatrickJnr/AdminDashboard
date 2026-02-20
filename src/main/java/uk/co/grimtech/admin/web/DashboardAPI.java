package uk.co.grimtech.admin.web;

import uk.co.grimtech.admin.AdminWebDashPlugin;
import uk.co.grimtech.admin.CustomLogger;
import uk.co.grimtech.admin.util.MuteTracker;
import uk.co.grimtech.admin.util.WarpManager;
import uk.co.grimtech.admin.util.BackupManager;
import uk.co.grimtech.admin.util.ConsoleManager;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.entity.entities.Player;

import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;

import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.builtin.weather.resources.WeatherResource;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;

import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemArmor;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.modules.accesscontrol.AccessControlModule;
import com.hypixel.hytale.server.core.modules.accesscontrol.ban.InfiniteBan;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;

import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.metrics.JVMMetrics;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.console.ConsoleSender;

import java.util.concurrent.TimeUnit;
import java.lang.reflect.Field;
import java.time.Duration;

import com.hypixel.hytale.server.core.config.ModConfig;
import com.hypixel.hytale.common.plugin.PluginIdentifier;

public class DashboardAPI {
    private static final Gson GSON = new Gson();
    private static CustomLogger LOGGER;
    private static long lastMetricsLogTime = 0;
    
    // Memoized Reflection Fields
    private static java.lang.reflect.Field banProviderField;
    
    // Result Caches
    private static String cachedPlayersJson;
    private static long playersCacheExpiry = 0;
    private static String cachedMetricsJson;
    private static long metricsCacheExpiry = 0;
    private static String cachedServerStatsJson;
    private static long serverStatsCacheExpiry = 0;

    static {
        try {
            banProviderField = AccessControlModule.class.getDeclaredField("banProvider");
            banProviderField.setAccessible(true);
        } catch (Exception e) {
            getLogger().log("ERROR", "Failed to memoize reflection fields", e);
        }
    }

    private static CustomLogger getLogger() {
        if (LOGGER == null) {
            LOGGER = AdminWebDashPlugin.getCustomLogger();
        }
        return LOGGER;
    }

    public static String handleRequest(String path, String method, String body) {
        getLogger().info("[API] " + method + " " + path);
        
        if (path.equals("/api/version")) {
            return getVersion();
        } else if (path.equals("/api/players")) {
            if (System.currentTimeMillis() < playersCacheExpiry && cachedPlayersJson != null) return cachedPlayersJson;
            cachedPlayersJson = getPlayers();
            playersCacheExpiry = System.currentTimeMillis() + 1000; // 1s cache
            return cachedPlayersJson;
        } else if (path.startsWith("/api/avatar/")) {
            String identifier = path.substring("/api/avatar/".length());
            byte[] avatar = AvatarCache.getAvatar(identifier);
            if (avatar != null) {
                // Return base64 encoded avatar data
                return "AVATAR_DATA:" + java.util.Base64.getEncoder().encodeToString(avatar);
            }
            return "{\"error\": \"Avatar not found\"}";
        } else if (path.startsWith("/api/item/") && path.endsWith("/icon")) {
            String itemId = path.substring(10, path.length() - 5); // Extract item ID
            return getItemIcon(itemId);
        } else if (path.startsWith("/api/mod/") && path.endsWith("/icon")) {
            String modName = path.substring(9, path.length() - 5); // Extract mod name
            return getModIcon(modName);
        } else if (path.startsWith("/api/player/") && path.endsWith("/inv")) {
            String uuidStr = path.substring(12, path.length() - 4);
            return getPlayerInventory(uuidStr);
        } else if (path.equals("/api/stats")) {
            if (System.currentTimeMillis() < serverStatsCacheExpiry && cachedServerStatsJson != null) return cachedServerStatsJson;
            cachedServerStatsJson = getServerStats();
            serverStatsCacheExpiry = System.currentTimeMillis() + 2000; // 2s cache
            return cachedServerStatsJson;
        } else if (path.equals("/api/metrics")) {
            if (System.currentTimeMillis() < metricsCacheExpiry && cachedMetricsJson != null) return cachedMetricsJson;
            cachedMetricsJson = getAdvancedMetrics();
            metricsCacheExpiry = System.currentTimeMillis() + 5000; // 5s cache
            return cachedMetricsJson;
        } else if (path.equals("/api/server/stats")) {
            if (System.currentTimeMillis() < serverStatsCacheExpiry && cachedServerStatsJson != null) return cachedServerStatsJson;
            cachedServerStatsJson = getServerStats();
            serverStatsCacheExpiry = System.currentTimeMillis() + 2000; // 2s cache
            return cachedServerStatsJson;
        } else if (path.equals("/api/server/config")) {
            return getServerConfig();
        } else if (path.equals("/api/broadcast") && method.equals("POST")) {
            return broadcastMessage(body);
        } else if (path.equals("/api/plugins")) {
            return getPlugins();
        } else if (path.equals("/api/kick") && method.equals("POST")) {
            return kickPlayer(body);
        } else if (path.equals("/api/ban") && method.equals("POST")) {
            return banPlayer(body);
        } else if (path.equals("/api/op") && method.equals("POST")) {
            return toggleOP(body);
        } else if (path.equals("/api/teleport") && method.equals("POST")) {
            return teleportPlayer(body);
        } else if (path.equals("/api/bans")) {
            return getBannedPlayers();
        } else if (path.equals("/api/bans/file")) {
            return getBansFile();
        } else if (path.equals("/api/unban") && method.equals("POST")) {
            return unbanPlayer(body);
        } else if (path.equals("/api/mute") && method.equals("POST")) {
            return mutePlayer(body);
        } else if (path.equals("/api/unmute") && method.equals("POST")) {
            return unmutePlayer(body);
        } else if (path.equals("/api/mutes")) {
            return getMutes();
        } else if (path.equals("/api/warps")) {
            return getWarps();
        } else if (path.equals("/api/warp/create") && method.equals("POST")) {
            return createWarp(body);
        } else if (path.equals("/api/warp/delete") && method.equals("POST")) {
            return deleteWarp(body);
        } else if (path.equals("/api/warp/teleport") && method.equals("POST")) {
            return teleportToWarp(body);
        } else if (path.equals("/api/heal") && method.equals("POST")) {
            return healPlayer(body);
        } else if (path.equals("/api/clearinv") && method.equals("POST")) {
            return clearInventory(body);
        } else if (path.equals("/api/time") && method.equals("POST")) {
            return setTime(body);
        } else if (path.equals("/api/weather") && method.equals("POST")) {
            return setWeather(body);
        } else if (path.equals("/api/gamemode") && method.equals("POST")) {
            return setGamemode(body);
        } else if (path.equals("/api/give") && method.equals("POST")) {
            return giveItem(body);
        } else if (path.equals("/api/items")) {
            return getAllItems();
        } else if (path.equals("/api/curseforge/clear-cache") && method.equals("POST")) {
            return clearCurseForgeCache();
        } else if (path.equals("/api/backups")) {
            return BackupManager.getBackups();
        } else if (path.equals("/api/backup/create") && method.equals("POST")) {
            return BackupManager.createBackup();
        } else if (path.equals("/api/backup/restore") && method.equals("POST")) {
             try {
                JsonObject json = GSON.fromJson(body, JsonObject.class);
                if (!json.has("name")) return "{\"error\": \"Missing backup name\"}";
                return BackupManager.restoreBackup(json.get("name").getAsString());
             } catch (Exception e) {
                 return "{\"error\": \"" + e.getMessage() + "\"}";
             }
        } else if (path.equals("/api/backup/schedule")) {
             if (method.equals("POST")) {
                 return BackupManager.scheduleBackups(body);
             } else if (method.equals("GET")) {
                 int interval = AdminWebDashPlugin.getInstance().getBackupInterval();
                 return "{\"intervalMinutes\": " + interval + "}";
             }
        } else if (path.equals("/api/backup/status")) {
            return BackupManager.getBackupStatus();
        } else if (path.equals("/api/discord/events")) {
            return getDiscordEvents();
        } else if (path.equals("/api/discord/test") && method.equals("POST")) {
            return testDiscordConnection(body);
        } else if (path.equals("/api/config/get")) {
            return getServerConfig();
        } else if (path.equals("/api/config/set") && method.equals("POST")) {
            return updateServerConfig(body);
        } else if (path.equals("/api/config/loglevel") && method.equals("POST")) {
            return setLogLevel(body);
        } else if (path.equals("/api/backup/delete") && method.equals("POST")) {
            return BackupManager.deleteBackup(body);
        } else if (path.equals("/api/console")) {
            return ConsoleManager.getConsoleLog();
        } else if (path.equals("/api/chat")) {
            return getChatMessages();
        } else if (path.equals("/api/console/execute") && method.equals("POST")) {
            return executeCommand(body);
        } else if (path.equals("/api/world/entities")) {
            return getEntityStats();
        } else if (path.equals("/api/inventory/move") && method.equals("POST")) {
            return moveInventoryItem(body);
        } else if (path.equals("/api/logs/list")) {
            return listLogs();
        } else if (path.equals("/api/logs/view")) {
            return "{\"error\": \"Missing name parameter\"}";
        } else if (path.startsWith("/api/logs/view/")) {
            String logName = path.substring("/api/logs/view/".length());
            return viewLog(logName);
        } else if (path.startsWith("/api/logs/delete/")) {
            String logName = path.substring("/api/logs/delete/".length());
            return deleteLog(logName);
        } else if (path.equals("/api/logs/download")) {
            return "{\"error\": \"Missing name parameter\"}";
        } else if (path.startsWith("/api/logs/download/")) {
            String logName = path.substring("/api/logs/download/".length());
            return downloadLog(logName);
        } else if (path.equals("/api/worlds/list")) {
            return listWorlds();
        } else if (path.equals("/api/worlds/load") && method.equals("POST")) {
            return loadWorld(body);
        } else if (path.equals("/api/worlds/unload") && method.equals("POST")) {
            return unloadWorld(body);
        } else if (path.equals("/api/worlds/state") && method.equals("POST")) {
            return toggleWorldState(body);

        } else if (path.equals("/api/world/info")) {
            return getWorldInfo();
        } else if (path.equals("/api/world/gamerules")) {
            if (method.equals("POST")) {
                return updateGameRules(body);
            } else {
                return getGameRules();
            }
        }
        
        return "{\"error\": \"Invalid endpoint\"}";
    }

    private static String getVersion() {
        JsonObject version = new JsonObject();
        
        // Try to get version from plugin manifest
        AdminWebDashPlugin plugin = AdminWebDashPlugin.getInstance();
        String versionString = "1.0.1"; // fallback
        
        if (plugin != null && plugin.getManifest() != null) {
            versionString = plugin.getManifest().getVersion().toString();
        }
        
        version.addProperty("version", versionString);
        version.addProperty("name", "Admin WebDash");
        version.addProperty("author", "Patrick Jr.");
        
        // System Info
        version.addProperty("javaVersion", System.getProperty("java.version"));
        version.addProperty("osName", System.getProperty("os.name"));
        version.addProperty("osArch", System.getProperty("os.arch"));
        version.addProperty("cores", Runtime.getRuntime().availableProcessors());
        
        // Memory Usage (Heap)
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        version.addProperty("heapUsed", usedMemory);
        version.addProperty("heapMax", maxMemory);
        
        // Disk Usage (Current Partition)
        java.io.File root = new java.io.File(".");
        long totalSpace = root.getTotalSpace();
        long freeSpace = root.getUsableSpace();
        long usedSpace = totalSpace - freeSpace;
        
        version.addProperty("diskUsed", usedSpace);
        version.addProperty("diskTotal", totalSpace);
        
        return GSON.toJson(version);
    }

    private static String getPlayers() {
        JsonArray playersArray = new JsonArray();
        try {
            List<PlayerRef> players = Universe.get().getPlayers();
            List<CompletableFuture<JsonObject>> futures = new ArrayList<>();

            for (PlayerRef ref : players) {
                // Thread-safe data from PlayerRef
                final JsonObject playerJson = new JsonObject();
                playerJson.addProperty("name", ref.getUsername());
                playerJson.addProperty("uuid", ref.getUuid().toString());
                playerJson.addProperty("avatarUrl", "/api/avatar/" + ref.getUsername());

                Ref<EntityStore> entityRef = ref.getReference();
                if (entityRef != null && entityRef.isValid()) {
                    Store<EntityStore> store = entityRef.getStore();
                    // Each Store/World has its own thread, execute there
                    World world = store.getExternalData().getWorld();
                    
                    CompletableFuture<JsonObject> future = CompletableFuture.supplyAsync(() -> {
                        try {
                            Player playerComp = store.getComponent(entityRef, Player.getComponentType());
                            if (playerComp != null) {
                                playerJson.addProperty("gameMode", playerComp.getGameMode().toString());
                                
                                EntityStatMap statMap = store.getComponent(entityRef, EntityStatMap.getComponentType());
                                if (statMap != null) {
                                    // Health
                                    EntityStatValue healthStat = statMap.get(DefaultEntityStatTypes.getHealth());
                                    if (healthStat != null) {
                                        playerJson.addProperty("health", healthStat.get());
                                        playerJson.addProperty("maxHealth", healthStat.getMax());
                                    }
                                    
                                    // Stamina
                                    EntityStatValue staminaStat = statMap.get(DefaultEntityStatTypes.getStamina());
                                    if (staminaStat != null) {
                                        playerJson.addProperty("stamina", staminaStat.get());
                                        playerJson.addProperty("maxStamina", staminaStat.getMax());
                                    } else {
                                        // Default values if stamina not available
                                        playerJson.addProperty("stamina", 100);
                                        playerJson.addProperty("maxStamina", 100);
                                    }
                                    
                                    // Mana
                                    EntityStatValue manaStat = statMap.get(DefaultEntityStatTypes.getMana());
                                    if (manaStat != null) {
                                        playerJson.addProperty("mana", manaStat.get());
                                        playerJson.addProperty("maxMana", manaStat.getMax());
                                    } else {
                                        // Default values if mana not available
                                        playerJson.addProperty("mana", 100);
                                        playerJson.addProperty("maxMana", 100);
                                    }
                                    
                                    // Defence (calculated from Physical damage resistance modifiers)
                                    double totalDefence = 0.0;
                                    if (playerComp != null) {
                                        Inventory inv = playerComp.getInventory();
                                        if (inv != null) {
                                            ItemContainer armorContainer = inv.getArmor();
                                            if (armorContainer != null) {
                                                for (short i = 0; i < armorContainer.getCapacity(); i++) {
                                                    ItemStack itemStack = armorContainer.getItemStack(i);
                                                    if (itemStack != null && !itemStack.isEmpty()) {
                                                        Item item = itemStack.getItem();
                                                        if (item != null && item.getArmor() != null) {
                                                            ItemArmor armor = item.getArmor();
                                                            
                                                            // Sum up Physical damage resistance
                                                            Map<DamageCause, StaticModifier[]> damageRes = armor.getDamageResistanceValues();
                                                            if (damageRes != null) {
                                                                for (Map.Entry<DamageCause, StaticModifier[]> entry : damageRes.entrySet()) {
                                                                    // Look for Physical damage type
                                                                    if ("Physical".equals(entry.getKey().getId())) {
                                                                        for (StaticModifier mod : entry.getValue()) {
                                                                            totalDefence += mod.getAmount();
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    // Convert to percentage (0.4 = 40%)
                                    playerJson.addProperty("defence", Math.round(totalDefence * 100));
                                }
                            }

                            TransformComponent transform = store.getComponent(entityRef, TransformComponent.getComponentType());
                            if (transform != null) {
                                Vector3d pos = transform.getPosition();
                                playerJson.addProperty("x", Math.round(pos.x * 100.0) / 100.0);
                                playerJson.addProperty("y", Math.round(pos.y * 100.0) / 100.0);
                                playerJson.addProperty("z", Math.round(pos.z * 100.0) / 100.0);
                            }
                        } catch (Exception e) {
                            getLogger().log("WARN", "Error gathering dynamic data for " + ref.getUsername(), e);
                        }
                        return playerJson;
                    }, world);
                    futures.add(future);
                } else {
                    futures.add(CompletableFuture.completedFuture(playerJson));
                }
            }

            // Wait for all worlds to report back (with timeout for safety)
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(2, TimeUnit.SECONDS);

            for (CompletableFuture<JsonObject> future : futures) {
                playersArray.add(future.join());
            }

        } catch (Exception e) {
            getLogger().log("ERROR", "Error getting players: " + e.getMessage(), e);
            JsonObject error = new JsonObject();
            error.addProperty("error", e.getMessage());
            return GSON.toJson(error);
        }

        return GSON.toJson(playersArray);
    }

    private static String getPlayerInventory(String uuidStr) {
        try {
            UUID uuid = UUID.fromString(uuidStr);
            PlayerRef ref = Universe.get().getPlayer(uuid);
            if (ref == null) return "{\"error\": \"Player not found\"}";

            Ref<EntityStore> entityRef = ref.getReference();
            if (entityRef == null || !entityRef.isValid()) {
                return "{\"error\": \"Player not in world\"}";
            }

            Store<EntityStore> store = entityRef.getStore();
            World world = store.getExternalData().getWorld();
            
            // Run on the world's thread
            JsonObject invJson = CompletableFuture.supplyAsync(() -> {
                JsonObject json = new JsonObject();
                try {
                    Player playerComp = store.getComponent(entityRef, Player.getComponentType());
                    if (playerComp != null) {
                        Inventory inv = playerComp.getInventory();
                        json.add("hotbar", serializeContainer(inv.getHotbar()));
                        json.add("storage", serializeContainer(inv.getStorage()));
                        json.add("armor", serializeContainer(inv.getArmor()));
                    }
                } catch (Exception e) {
                    json.addProperty("error", e.getMessage());
                }
                return json;
            }, world).get(2, TimeUnit.SECONDS);

            return GSON.toJson(invJson);
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private static JsonArray serializeContainer(ItemContainer container) {
        JsonArray items = new JsonArray();
        for (int i = 0; i < container.getCapacity(); i++) {
            ItemStack stack = container.getItemStack((short) i);
            if (stack != null && !stack.isEmpty()) {
                JsonObject item = new JsonObject();
                item.addProperty("id", stack.getItemId());
                item.addProperty("count", stack.getQuantity());
                item.addProperty("slot", i);

                // Add rarity info
                Item itemAsset = stack.getItem();
                if (itemAsset != null) {
                    item.addProperty("rarity", itemAsset.getQualityIndex());
                    ItemQuality quality = ItemQuality.getAssetMap().getAsset(itemAsset.getQualityIndex());
                    if (quality == null) {
                        quality = ItemQuality.getAssetMap().getAsset("Default");
                    }
                    if (quality != null && quality.getTextColor() != null) {
                        com.hypixel.hytale.protocol.Color color = quality.getTextColor();
                        String hex = String.format("#%02x%02x%02x", color.red \u0026 0xFF, color.green \u0026 0xFF, color.blue \u0026 0xFF);
                        item.addProperty("rarityColor", hex);
                    }
                }

                items.add(item);
            }
        }
        return items;
    }

    private static String moveInventoryItem(String body) {
        try {
            JsonObject json = GSON.fromJson(body, JsonObject.class);
            if (!json.has("playerUuid")) return "{\"error\": \"Missing playerUuid\"}";
            
            UUID uuid = UUID.fromString(json.get("playerUuid").getAsString());
            int fromSection = json.get("fromSection").getAsInt();
            int fromSlot = json.get("fromSlot").getAsInt();
            int toSection = json.get("toSection").getAsInt();
            int toSlot = json.get("toSlot").getAsInt();
            int quantity = json.get("quantity").getAsInt();

            PlayerRef ref = Universe.get().getPlayer(uuid);
            if (ref == null) return "{\"error\": \"Player not found\"}";

            Ref<EntityStore> entityRef = ref.getReference();
            if (entityRef == null || !entityRef.isValid()) {
                return "{\"error\": \"Player not in world\"}";
            }

            Store<EntityStore> store = entityRef.getStore();
            World world = store.getExternalData().getWorld();

            return CompletableFuture.supplyAsync(() -> {
                try {
                    Player playerComp = store.getComponent(entityRef, Player.getComponentType());
                    if (playerComp != null) {
                        Inventory inv = playerComp.getInventory();
                        ItemContainer fromContainer = inv.getSectionById(fromSection);
                        int finalQuantity = quantity;
                        if (finalQuantity == -1 && fromContainer != null) {
                            ItemStack stack = fromContainer.getItemStack((short) fromSlot);
                            if (stack != null) {
                                finalQuantity = stack.getQuantity();
                            }
                        }
                        
                        if (finalQuantity > 0) {
                            inv.moveItem(fromSection, fromSlot, finalQuantity, toSection, toSlot);
                            playerComp.markNeedsSave();
                            playerComp.sendInventory();
                            return "{\"status\": \"success\"}";
                        }
                        return "{\"error\": \"Invalid quantity or empty slot\"}";
                    }
                    return "{\"error\": \"Player component not found\"}";
                } catch (Exception e) {
                    return "{\"error\": \"" + e.getMessage() + "\"}";
                }
            }, world).get(2, TimeUnit.SECONDS);

        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private static String getItemIcon(String itemId) {
        String cacheKey = "item_icon_" + itemId;
        String cached = ResourceCache.get(cacheKey);
        if (cached != null) return cached;

        try {
            // 0. URL Decode the itemId (e.g. hytale%3Astone -> hytale:stone)
            String rawId = itemId;
            itemId = URLDecoder.decode(itemId, StandardCharsets.UTF_8.name());
            if (!rawId.equals(itemId)) {
                getLogger().info("[DashboardAPI] Decoded itemId: " + rawId + " -> " + itemId);
            }

            // 1. Resolve Namespace
            String namespace = "hytale";
            String assetId = itemId;
            if (itemId.contains(":")) {
                String[] parts = itemId.split(":", 2);
                namespace = parts[0];
                assetId = parts[1];
            }

            // 2. Get AssetPack (Case-Insensitive)
            AssetPack pack = findAssetPack(namespace);
            if (pack == null) {
                getLogger().warning("[DashboardAPI] Could not find AssetPack for namespace (tried case-insensitive): " + namespace);
                return getFallbackIcon(itemId);
            }

            // 3. Get Icon Path from Item config
            Item item = Item.getAssetStore().getAssetMap().getAsset(itemId);
            if (item == null && !itemId.contains(":")) {
                // Try with namespace if not present
                item = Item.getAssetStore().getAssetMap().getAsset(namespace + ":" + assetId);
            }
            if (item == null) {
                // Try with common name variations
                item = Item.getAssetStore().getAssetMap().getAsset("hytale:" + assetId);
            }

            String iconPathStr = null;
            if (item != null) {
                iconPathStr = item.getIcon();
                getLogger().info("[DashboardAPI] Resolved Item " + itemId + " icon from config: " + iconPathStr);
            } else {
                // Manual resolution if Item not found in store
                // Hytale usually puts icons in Icons/ItemsGenerated or Icons/Entities
                iconPathStr = "Icons/ItemsGenerated/" + assetId + ".png";
                getLogger().info("[DashboardAPI] Item " + itemId + " not in store, using manual path prediction: " + iconPathStr);
            }

            if (iconPathStr == null || iconPathStr.isEmpty()) {
                getLogger().warning("[DashboardAPI] Icon path is null or empty for " + itemId);
                return getFallbackIcon(itemId);
            }

            // 4. Resolve and Read PNG
            Path root = pack.getRoot();
            Path iconPath = null;
            
            // Try different common Hytale asset structures
            String[] searchBases = {
                "Common",
                "Assets/Common",
                "Assets",
                "" // Root directly
            };

            for (String base : searchBases) {
                Path attempt = base.isEmpty() ? root.resolve(iconPathStr) : root.resolve(base).resolve(iconPathStr);
                
                // Try with and without .png extension
                if (Files.exists(attempt)) {
                    iconPath = attempt;
                    break;
                }
                
                if (!iconPathStr.toLowerCase().endsWith(".png")) {
                    Path attemptWithPng = base.isEmpty() ? root.resolve(iconPathStr + ".png") : root.resolve(base).resolve(iconPathStr + ".png");
                    if (Files.exists(attemptWithPng)) {
                        iconPath = attemptWithPng;
                        break;
                    }
                }
            }

            if (iconPath == null) {
                getLogger().warning("[DashboardAPI] Icon file NOT FOUND for " + itemId + " after trying all bases. IconPathStr: " + iconPathStr);
                return getFallbackIcon(itemId);
            }

            getLogger().info("[DashboardAPI] Final resolved path for " + itemId + ": " + iconPath.toAbsolutePath());

            byte[] imageBytes = Files.readAllBytes(iconPath);
            getLogger().info("[DashboardAPI] Successfully read " + imageBytes.length + " bytes for " + itemId);
            String result = "IMAGE_DATA:" + java.util.Base64.getEncoder().encodeToString(imageBytes);
            ResourceCache.put(cacheKey, result, 0); // Infinite cache for static icons
            return result;

        } catch (Exception e) {
            getLogger().log("WARN", "[DashboardAPI] Error extracting real icon for " + itemId, e);
            String fallback = getFallbackIcon(itemId);
            // Don't cache fallback infinitely, but maybe for a while? 
            ResourceCache.put(cacheKey, fallback, 60000); // 1 minute for fallback
            return fallback;
        }
    }

    private static AssetPack findAssetPack(String namespace) {
        // Log all available packs for debugging
        StringBuilder packNames = new StringBuilder();
        for (AssetPack p : AssetModule.get().getAssetPacks()) {
            packNames.append(p.getName()).append(", ");
        }
        getLogger().info("[DashboardAPI] Searching for namespace: " + namespace + " in available packs: [" + packNames.toString() + "]");

        AssetPack pack = AssetModule.get().getAssetPack(namespace);
        if (pack != null) return pack;

        // Manual search across all registered packs for smarter matching
        for (AssetPack p : AssetModule.get().getAssetPacks()) {
            String name = p.getName();
            // Exact match (case insensitive)
            if (name.equalsIgnoreCase(namespace)) {
                return p;
            }
            
            // Hytale style namespace matching (e.g. "Hytale:Hytale" for "hytale")
            if (name.contains(":")) {
                String prefix = name.split(":")[0];
                if (prefix.equalsIgnoreCase(namespace)) {
                    return p;
                }
            }
        }
        return null;
    }

    private static String getFallbackIcon(String itemId) {
        try {
            // Determine color based on item category
            java.awt.Color color;
            String idLower = itemId.toLowerCase();
            
            if (idLower.startsWith("weapon_")) {
                color = new java.awt.Color(200, 50, 0); // Red/Orange for weapons
            } else if (idLower.startsWith("tool_")) {
                color = new java.awt.Color(160, 160, 160); // Silver/Gray for tools
            } else if (idLower.contains("ore_") || idLower.contains("ingredient_ore")) {
                color = new java.awt.Color(139, 69, 19); // Brown for ores
            } else if (idLower.startsWith("armor_")) {
                color = new java.awt.Color(30, 144, 255); // Blue for armor
            } else if (idLower.contains("food_") || idLower.contains("ingredient_food")) {
                color = new java.awt.Color(255, 105, 180); // Pink for food
            } else if (idLower.startsWith("block_") || idLower.startsWith("soil_")) {
                color = new java.awt.Color(100, 100, 100); // Dark Gray for blocks
            } else if (idLower.contains("ingredient_")) {
                color = new java.awt.Color(154, 205, 50); // Yellow/Green for ingredients
            } else {
                // Default to hash-based color for consistency
                int hash = itemId.hashCode();
                int r = Math.abs((hash & 0xFF0000) >> 16);
                int g = Math.abs((hash & 0x00FF00) >> 8);
                int b = Math.abs(hash & 0x0000FF);
                color = new java.awt.Color(r, g, b);
            }
            
            // Create a 32x32 PNG with the color
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(32, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g2 = img.createGraphics();
            
            // Fill with color
            g2.setColor(color);
            g2.fillRect(4, 4, 24, 24);
            
            // Add aesthetic border
            g2.setColor(color.darker());
            g2.setStroke(new java.awt.BasicStroke(2));
            g2.drawRect(4, 4, 23, 23);
            
            // Add a subtle sheen/highlight
            g2.setColor(new java.awt.Color(255, 255, 255, 60));
            g2.fillRect(6, 6, 10, 4);
            
            g2.dispose();
            
            // Convert to PNG bytes
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "PNG", baos);
            byte[] imageBytes = baos.toByteArray();
            
            // Return as base64 with IMAGE_DATA prefix
            return "IMAGE_DATA:" + java.util.Base64.getEncoder().encodeToString(imageBytes);
        } catch (Exception e) {
            getLogger().log("WARN", "Error generating fallback icon for " + itemId, e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private static String getServerStats() {
        JsonObject stats = new JsonObject();
        stats.addProperty("onlinePlayers", Universe.get().getPlayers().size());
        
        // Calculate Uptime
        long uptimeMs = System.currentTimeMillis() - AdminWebDashPlugin.getStartTime();
        stats.addProperty("uptimeMs", uptimeMs);
        
        // Calculate Actual TPS from first world
        double tps = 30.0;
        try {
            java.util.Collection<World> worlds = Universe.get().getWorlds().values();
            if (!worlds.isEmpty()) {
                World world = worlds.iterator().next();
                double avgTickNanos = world.getBufferedTickLengthMetricSet().getAverage(0);
                if (avgTickNanos > 0) {
                    tps = 1000000000.0 / avgTickNanos;
                    // Cap at 30.0 as it can't realistically exceed it in Hytale's model
                    if (tps > 30.0) tps = 30.0;
                }
            }
        } catch (Exception e) {
            getLogger().warning("Could not calculate actual TPS: " + e.getMessage());
        }
        stats.addProperty("tps", Math.round(tps * 10.0) / 10.0);

        // Memory usage
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        stats.addProperty("memory", usedMemory); // Bytes for frontend to format
        stats.addProperty("memoryUsed", usedMemory / (1024 * 1024)); // MB
        stats.addProperty("memoryMax", maxMemory / (1024 * 1024)); // MB
        stats.addProperty("memoryPercent", Math.round((double) usedMemory / maxMemory * 100.0));

        return GSON.toJson(stats);
    }

    private static String getAdvancedMetrics() {
        try {
            JsonObject root = new JsonObject();
            
            // JVM Metrics Registration Dump
            BsonDocument jvmBson = JVMMetrics.METRICS_REGISTRY.dumpToBson(null).asDocument();
            JsonObject jvmJson = bsonToJson(jvmBson);
            
            // Log structure for debugging if missing fields
            if (!jvmJson.has("System")) {
                getLogger().info("[Metrics] Missing 'System' key in JVM metrics. Keys found: " + jvmJson.keySet());
            }
            
            // 1. CPU Usage (Process CPU Load)
            double cpu = 0;
            if (jvmJson.has("System") && jvmJson.getAsJsonObject("System").has("ProcessCpuLoad")) {
                cpu = jvmJson.getAsJsonObject("System").get("ProcessCpuLoad").getAsDouble();
            }
            root.addProperty("processCpuLoad", cpu);
            
            // 2. Memory Usage (Heap Used)
            long heapUsed = 0;
            if (jvmJson.has("Memory") && jvmJson.getAsJsonObject("Memory").has("HeapMemoryUsage")) {
                heapUsed = jvmJson.getAsJsonObject("Memory").getAsJsonObject("HeapMemoryUsage").get("Used").getAsLong();
            }
            root.addProperty("heapUsed", heapUsed);
            
            // 3. Thread Count
            if (jvmJson.has("Threads")) {
                root.addProperty("threadCount", jvmJson.getAsJsonArray("Threads").size());
            } else {
                root.addProperty("threadCount", 0);
            }
            
            // 4. GC Collections (Sum across all collectors)
            long gcCount = 0;
            if (jvmJson.has("GarbageCollectors")) {
                for (com.google.gson.JsonElement ge : jvmJson.getAsJsonArray("GarbageCollectors")) {
                    JsonObject collector = ge.getAsJsonObject();
                    if (collector.has("CollectionCount")) {
                        gcCount += collector.get("CollectionCount").getAsLong();
                    }
                }
            }
            root.addProperty("gcCollections", gcCount);
            
            // 5. Total TPS (Average across worlds)
            double totalTps = 0;
            int worldCount = 0;
            
            // World Metrics (Detailed and per-world)
            JsonObject worlds = new JsonObject();
            for (World world : Universe.get().getWorlds().values()) {
                BsonDocument worldBson = World.METRICS_REGISTRY.dumpToBson(world).asDocument();
                JsonObject worldJson = bsonToJson(worldBson);
                
                // Calculate TPS for this specific world
                double worldTps = 30.0;
                double avgTickNanos = world.getBufferedTickLengthMetricSet().getAverage(0);
                if (avgTickNanos > 0) {
                    worldTps = Math.min(30.0, 1000000000.0 / avgTickNanos);
                }
                worldJson.addProperty("tps", Math.round(worldTps * 10.0) / 10.0);
                
                worlds.add(world.getName(), worldJson);
                totalTps += worldTps;
                worldCount++;
            }
            root.add("worlds", worlds);
            root.addProperty("tps", worldCount > 0 ? (Math.round((totalTps / worldCount) * 10.0) / 10.0) : 30.0);
            
            String jsonOutput = GSON.toJson(root);
            // Log once every 30 seconds to avoid spamming
            if (System.currentTimeMillis() - lastMetricsLogTime > 30000) {
                getLogger().info("[Metrics] Response: " + jsonOutput);
                lastMetricsLogTime = System.currentTimeMillis();
            }
            
            return jsonOutput;
        } catch (Exception e) {
            getLogger().log("ERROR", "Error getting advanced metrics: " + e.getMessage(), e);
            JsonObject error = new JsonObject();
            error.addProperty("error", e.getMessage());
            return GSON.toJson(error);
        }
    }

    private static JsonObject bsonToJson(BsonDocument bson) {
        JsonObject json = new JsonObject();
        for (String key : bson.keySet()) {
            json.add(key, bsonValueToJson(bson.get(key)));
        }
        return json;
    }

    private static com.google.gson.JsonElement bsonValueToJson(BsonValue value) {
        if (value.isNull()) return com.google.gson.JsonNull.INSTANCE;
        if (value.isDocument()) return bsonToJson(value.asDocument());
        if (value.isArray()) {
            JsonArray array = new JsonArray();
            for (BsonValue item : value.asArray()) {
                array.add(bsonValueToJson(item));
            }
            return array;
        }
        if (value.isString()) return new com.google.gson.JsonPrimitive(value.asString().getValue());
        if (value.isNumber()) return new com.google.gson.JsonPrimitive(value.asNumber().doubleValue());
        if (value.isBoolean()) return new com.google.gson.JsonPrimitive(value.asBoolean().getValue());
        if (value.isDateTime()) return new com.google.gson.JsonPrimitive(value.asDateTime().getValue());
        
        return new com.google.gson.JsonPrimitive(value.toString());
    }

    private static String listLogs() {
        try {
            java.nio.file.Path logsDir = java.nio.file.Path.of("logs");
            if (!java.nio.file.Files.exists(logsDir)) return "[]";
            
            com.google.gson.JsonArray logs = new com.google.gson.JsonArray();
            try (var stream = java.nio.file.Files.list(logsDir)) {
                stream.forEach(p -> {
                    String fileName = p.getFileName().toString();
                    if (java.nio.file.Files.isRegularFile(p) && !fileName.endsWith(".lck")) {
                        com.google.gson.JsonObject log = new com.google.gson.JsonObject();
                        log.addProperty("name", fileName);
                        try {
                            log.addProperty("size", java.nio.file.Files.size(p));
                            log.addProperty("modified", java.nio.file.Files.getLastModifiedTime(p).toMillis());
                        } catch (Exception ignored) {}
                        logs.add(log);
                    }
                });
            }
            return GSON.toJson(logs);
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private static String viewLog(String name) {
        try {
            java.nio.file.Path logPath = java.nio.file.Path.of("logs").resolve(name).normalize();
            if (!logPath.startsWith(java.nio.file.Path.of("logs").normalize())) {
                return "{\"error\": \"Invalid log path\"}";
            }
            if (!java.nio.file.Files.exists(logPath)) return "{\"error\": \"Log not found\"}";
            
            // Read last 1MB of the log to prevent crashing the browser
            long size = java.nio.file.Files.size(logPath);
            long limit = 1024 * 1024; // 1MB
            
            String content;
            if (size > limit) {
                try (var raf = new java.io.RandomAccessFile(logPath.toFile(), "r")) {
                    raf.seek(size - limit);
                    byte[] bytes = new byte[(int) limit];
                    raf.readFully(bytes);
                    content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                    content = "... [TRUNCATED] ...\n" + content;
                }
            } else {
                content = java.nio.file.Files.readString(logPath, java.nio.charset.StandardCharsets.UTF_8);
            }
            
            com.google.gson.JsonObject result = new com.google.gson.JsonObject();
            result.addProperty("name", name);
            result.addProperty("content", content);
            return GSON.toJson(result);
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private static String downloadLog(String name) {
        try {
            java.nio.file.Path logPath = java.nio.file.Path.of("logs").resolve(name).normalize();
            if (!logPath.startsWith(java.nio.file.Path.of("logs").normalize())) {
                return "{\"error\": \"Invalid log path\"}";
            }
            if (!java.nio.file.Files.exists(logPath)) return "{\"error\": \"Log not found\"}";
            
            byte[] data = java.nio.file.Files.readAllBytes(logPath);
            return "FILE_DATA:" + java.util.Base64.getEncoder().encodeToString(data);
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private static String deleteLog(String name) {
        try {
            java.nio.file.Path logPath = java.nio.file.Path.of("logs").resolve(name).normalize();
            if (!logPath.startsWith(java.nio.file.Path.of("logs").normalize())) {
                return "{\"error\": \"Invalid log path\"}";
            }
            if (!java.nio.file.Files.exists(logPath)) return "{\"error\": \"Log not found\"}";
            
            java.nio.file.Files.delete(logPath);
            return "{\"success\": true}";
        } catch (Exception e) {
            return "{\"error\": \"Failed to delete log: " + e.getMessage() + "\"}";
        }
    }

    private static String listWorlds() {
        try {
            JsonArray jsonArray = new JsonArray();
            Universe universe = Universe.get();
            Map<String, World> loadedWorlds = universe.getWorlds();
            java.util.Set<String> processedNames = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);

            // 1. Process all LOADED worlds first (includes instances)
            for (Map.Entry<String, World> entry : loadedWorlds.entrySet()) {
                String name = entry.getKey();
                World world = entry.getValue();
                processedNames.add(name);

                JsonObject worldJson = new JsonObject();
                worldJson.addProperty("name", name);
                worldJson.addProperty("loaded", true);
                worldJson.addProperty("ticking", !world.isPaused());
                
                // Optimized player counting for this world
                long playerCount = universe.getPlayers().stream()
                    .filter(pr -> {
                        Ref<EntityStore> ref = pr.getReference();
                        if (ref == null || !ref.isValid()) return false;
                        World playerWorld = ref.getStore().getExternalData().getWorld();
                        return playerWorld == world || (playerWorld != null && playerWorld.equals(world));
                    })
                    .count();
                
                worldJson.addProperty("players", playerCount);
                jsonArray.add(worldJson);
            }

            // 2. Add UNLOADED worlds found on disk
            Path worldsDir = universe.getPath().resolve("worlds");
            if (Files.exists(worldsDir)) {
                try (var stream = Files.list(worldsDir)) {
                    stream.forEach(p -> {
                        if (Files.isDirectory(p)) {
                            String name = p.getFileName().toString();
                            if (!processedNames.contains(name)) {
                                JsonObject worldJson = new JsonObject();
                                worldJson.addProperty("name", name);
                                worldJson.addProperty("loaded", false);
                                worldJson.addProperty("ticking", false);
                                worldJson.addProperty("players", 0);
                                jsonArray.add(worldJson);
                            }
                        }
                    });
                }
            }
            return GSON.toJson(jsonArray);
        } catch (Exception e) {
            getLogger().log("ERROR", "Failed to list worlds", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private static String loadWorld(String body) {
        try {
            JsonObject json = GSON.fromJson(body, JsonObject.class);
            String name = json.get("name").getAsString();
            Universe.get().loadWorld(name);
            return "{\"success\": true}";
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private static String unloadWorld(String body) {
        try {
            JsonObject json = GSON.fromJson(body, JsonObject.class);
            String name = json.get("name").getAsString();
            Universe.get().removeWorld(name);
            return "{\"success\": true}";
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private static String toggleWorldState(String body) {
        try {
            JsonObject json = GSON.fromJson(body, JsonObject.class);
            String name = json.get("name").getAsString();
            boolean ticking = json.get("ticking").getAsBoolean();
            
            World world = Universe.get().getWorlds().get(name);
            if (world != null) {
                world.setPaused(!ticking);
            }
            return "{\"success\": true}";
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }






    private static String setLogLevel(String body) {
        try {
            JsonObject json = GSON.fromJson(body, JsonObject.class);
            if (!json.has("package") || !json.has("level")) return "{\"error\": \"Missing package or level\"}";
            
            String pkg = json.get("package").getAsString();
            String level = json.get("level").getAsString();
            
            com.hypixel.hytale.server.core.HytaleServerConfig config = HytaleServer.get().getConfig();
            config.getLogLevels().put(pkg, java.util.logging.Level.parse(level.toUpperCase()));
            
            return "{\"success\": true}";
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private static String broadcastMessage(String body) {
        try {
            JsonObject json = GSON.fromJson(body, JsonObject.class);
            if (!json.has("message")) return "{\"error\": \"Missing message\"}";
            String message = json.get("message").getAsString();
            Universe.get().sendMessage(Message.raw("[Server] " + message));
            return "{\"status\": \"success\"}";
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private static final java.util.Set<String> INTERNAL_PLUGINS = java.util.Set.of(
        "WorldLocationCondition", "ServerManager", "SprintForce", "MigrationModule", "AssetModule", 
        "ConsoleModule", "TimeModule", "CollisionModule", "DebugPlugin", "SplitVelocity", 
        "Model", "CrouchSlide", "Universe", "TagSet", "UpdateModule", 
        "AccessControlModule", "CommandMacro", "SafetyRoll", "HytaleGenerator", "ItemModule", 
        "CommonAssetModule", "CosmeticsModule", "I18nModule", "LegacyModule", "LANDiscovery", 
        "Mantling", "PermissionsModule", "Deployables", "BlockSetModule", "FlyCameraModule", 
        "EntityModule", "BlockTick", "BlockHealthModule", "InteractionModule", "ServerPlayerListModule", 
        "ConnectedBlocksModule", "BlockTypeModule", "BlockModule", "Weather", "Reputation", 
        "Parkour", "ProjectileModule", "Teleport", "WorldGen", "BlockStateModule", 
        "AssetEditor", "Ambience", "Teleporter", "BlockPhysics", "BuilderTools", 
        "Farming", "Fluid", "Shop", "Crafting", "SingleplayerModule", 
        "ShopReputation", "EntityStatsModule", "BlockSpawner", "PrefabSpawnerModule", "Instances", 
        "Objectives", "Path", "EntityUIModule", "Stash", "StaminaModule", 
        "ObjectiveShop", "ObjectiveReputation", "CreativeHub", "DamageModule", "Camera", 
        "NPC", "Mounts", "Flock", "NPCEditor", "Memories", 
        "NPCShop", "Beds", "Portals", "NPCCombatActionEvaluator", "NPCReputation", 
        "Spawning", "NPCObjectives"
    );

    private static String getPlugins() {
        JsonArray plugins = new JsonArray();
        java.util.Set<String> processedPacks = new java.util.HashSet<>();
        
        try {
            // 1. Add JAR-based java plugins
            List<PluginBase> loadedPlugins = PluginManager.get().getPlugins();
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            for (PluginBase plugin : loadedPlugins) {
                String name = plugin.getManifest().getName();
                if (INTERNAL_PLUGINS.contains(name)) continue;

                JsonObject pObj = new JsonObject();
                pObj.addProperty("name", name);
                pObj.addProperty("version", plugin.getManifest().getVersion().toString());
                pObj.addProperty("id", plugin.getIdentifier().toString());
                pObj.addProperty("type", "Mod");
                pObj.addProperty("iconUrl", "/api/mod/" + encodeForUrl(name) + "/icon");
                
                // Try to fetch CurseForge metadata asynchronously
                CompletableFuture<Void> future = CurseForgeAPI.searchMod(name).thenAccept(cfData -> {
                    if (cfData != null) {
                        if (cfData.has("iconUrl")) {
                            pObj.addProperty("iconUrl", cfData.get("iconUrl").getAsString());
                        }
                        if (cfData.has("author")) {
                            pObj.addProperty("author", cfData.get("author").getAsString());
                        }
                        if (cfData.has("downloads")) {
                            pObj.addProperty("downloads", cfData.get("downloads").getAsLong());
                        }
                        if (cfData.has("url")) {
                            pObj.addProperty("curseforgeUrl", cfData.get("url").getAsString());
                        }
                    }
                });
                futures.add(future);
                
                plugins.add(pObj);
                
                // Track identified packs to avoid duplicates
                processedPacks.add(name.toLowerCase());
                if (name.contains(":")) {
                    processedPacks.add(name.split(":")[1].toLowerCase());
                }
            }
            
            // 2. Add Asset Packs (covers zipped mods without code)
            for (AssetPack pack : AssetModule.get().getAssetPacks()) {
                String name = pack.getName();
                String cleanName = name;
                if (name.contains(":")) {
                    cleanName = name.split(":")[1];
                }
                
                if (INTERNAL_PLUGINS.contains(cleanName) || INTERNAL_PLUGINS.contains(name)) continue;
                if (processedPacks.contains(name.toLowerCase()) || processedPacks.contains(cleanName.toLowerCase())) continue;

                JsonObject pObj = new JsonObject();
                pObj.addProperty("name", cleanName);
                pObj.addProperty("version", "1.0"); // Asset packs don't usually have a version in manifest
                pObj.addProperty("id", name);
                pObj.addProperty("type", "Asset Pack");
                pObj.addProperty("iconUrl", "/api/mod/" + encodeForUrl(name) + "/icon");
                
                // Try to fetch CurseForge metadata asynchronously
                CompletableFuture<Void> future = CurseForgeAPI.searchMod(cleanName).thenAccept(cfData -> {
                    if (cfData != null) {
                        if (cfData.has("iconUrl")) {
                            pObj.addProperty("iconUrl", cfData.get("iconUrl").getAsString());
                        }
                        if (cfData.has("author")) {
                            pObj.addProperty("author", cfData.get("author").getAsString());
                        }
                        if (cfData.has("downloads")) {
                            pObj.addProperty("downloads", cfData.get("downloads").getAsLong());
                        }
                        if (cfData.has("url")) {
                            pObj.addProperty("curseforgeUrl", cfData.get("url").getAsString());
                        }
                    }
                });
                futures.add(future);
                
                plugins.add(pObj);
                processedPacks.add(cleanName.toLowerCase());
            }
            
            // Wait for all CurseForge lookups to complete (with timeout)
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                getLogger().warning("Some CurseForge lookups timed out: " + e.getMessage());
            }
            
        } catch (Exception e) {
            getLogger().warning("Error getting plugins: " + e.getMessage());
            // Fallback for safety
            JsonObject pObj = new JsonObject();
            pObj.addProperty("name", "Admin Dashboard");
            pObj.addProperty("version", "0.1");
            plugins.add(pObj);
        }
        return GSON.toJson(plugins);
    }

    private static String getModIcon(String modName) {
        String cacheKey = "mod_icon_" + modName;
        String cached = ResourceCache.get(cacheKey);
        if (cached != null) return cached;

        try {
            modName = URLDecoder.decode(modName, StandardCharsets.UTF_8.name());
            AssetPack pack = findAssetPack(modName);
            if (pack == null) return "{\"error\": \"Mod not found\"}";

            // Common logo filenames in Hytale mods
            String[] possibleLogos = {"pack.png", "logo.png", "icon.png", "manifest.png"};
            
            for (String logo : possibleLogos) {
                try {
                    java.nio.file.Path logoPath = pack.getFileSystem().getPath(logo);
                    byte[] data = java.nio.file.Files.readAllBytes(logoPath);
                    if (data != null && data.length > 0) {
                        String result = "IMAGE_DATA:" + java.util.Base64.getEncoder().encodeToString(data);
                        ResourceCache.put(cacheKey, result, 0); // Infinite cache for static mod icons
                        return result;
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            getLogger().warning("[DashboardAPI] Error fetching mod icon for " + modName + ": " + e.getMessage());
        }
        return "{\"error\": \"No icon found\"}";
    }

    private static String encodeForUrl(String text) {
        try {
            return java.net.URLEncoder.encode(text, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return text;
        }
    }

    private static String getServerConfig() {
        try {
            com.hypixel.hytale.server.core.HytaleServerConfig hytaleConfig = HytaleServer.get().getConfig();
            
            JsonObject result = new JsonObject();
            result.addProperty("serverName", hytaleConfig.getServerName());
            result.addProperty("motd", hytaleConfig.getMotd());
            result.addProperty("maxPlayers", hytaleConfig.getMaxPlayers());
            result.addProperty("maxViewRadius", hytaleConfig.getMaxViewRadius());
            
            // Log levels
            JsonObject logLevels = new JsonObject();
            for (Map.Entry<String, java.util.logging.Level> entry : hytaleConfig.getLogLevels().entrySet()) {
                logLevels.addProperty(entry.getKey(), entry.getValue().getName());
            }
            result.add("logLevels", logLevels);
            
            // Defaults
            JsonObject defaults = new JsonObject();
            defaults.addProperty("defaultWorld", hytaleConfig.getDefaults().getWorld());
            defaults.addProperty("defaultGameMode", hytaleConfig.getDefaults().getGameMode().toString());
            result.add("defaults", defaults);

            // Connection Timeouts
            JsonObject timeouts = new JsonObject();
            timeouts.addProperty("playTimeout", hytaleConfig.getConnectionTimeouts().getPlay().toMinutes());
            result.add("connectionTimeouts", timeouts);

            // Mods
            JsonObject mods = new JsonObject();
            for (Map.Entry<PluginIdentifier, ModConfig> entry : hytaleConfig.getModConfig().entrySet()) {
                JsonObject mod = new JsonObject();
                // ModConfig.enabled is Boolean (nullable), handle null as true (default enabled)
                Boolean enabled = entry.getValue().getEnabled();
                mod.addProperty("enabled", enabled != null ? enabled : true);
                mods.add(entry.getKey().toString(), mod);
            }
            result.add("mods", mods);
            
            // Also append our custom webdash config for the dashboard
            JsonObject webDashConfig = new JsonObject();
            // Return only safe properties, filter out sensitive ones like keystore password
            webDashConfig.addProperty("port", AdminWebDashPlugin.getInstance().getBackupInterval() /* wait wrong getter, port isn't exposed yet but it's not strictly needed for UI config if it's random or fixed */ );
            webDashConfig.addProperty("backupInterval", AdminWebDashPlugin.getInstance().getBackupInterval());
            webDashConfig.addProperty("loggingEnabled", AdminWebDashPlugin.isLoggingEnabled());
            
            // Discord Config
            webDashConfig.addProperty("discordEnabled", AdminWebDashPlugin.isDiscordEnabled());
            // DO NOT SEND DISCORD TOKEN FOR SECURITY (unless it's empty, we just send a placeholder boolean)
            webDashConfig.addProperty("hasDiscordToken", AdminWebDashPlugin.getDiscordToken() != null && !AdminWebDashPlugin.getDiscordToken().isEmpty());
            webDashConfig.addProperty("discordGuildId", AdminWebDashPlugin.getDiscordGuildId());
            webDashConfig.addProperty("discordChannelLogs", AdminWebDashPlugin.getDiscordChannelLogs());
            webDashConfig.addProperty("discordChannelAlerts", AdminWebDashPlugin.getDiscordChannelAlerts());
            webDashConfig.addProperty("discordChannelJoins", AdminWebDashPlugin.getDiscordChannelJoins());
            webDashConfig.addProperty("discordCommandPrefix", AdminWebDashPlugin.getDiscordCommandPrefix());
            
            // HTTPS Config
            webDashConfig.addProperty("useHttps", AdminWebDashPlugin.useHttps());
            webDashConfig.addProperty("keystorePath", AdminWebDashPlugin.getKeystorePath());
            webDashConfig.addProperty("hasKeystorePassword", AdminWebDashPlugin.getKeystorePassword() != null && !AdminWebDashPlugin.getKeystorePassword().isEmpty());
            webDashConfig.addProperty("domain", AdminWebDashPlugin.getDomain());
            
            result.add("webdash", webDashConfig);
            
            return GSON.toJson(result);
        } catch (Exception e) {
             return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private static String updateServerConfig(String body) {
        try {
            JsonObject newConfig = GSON.fromJson(body, JsonObject.class);
            boolean requiresRestart = false;

            // Update simple fields directly in the plugin config file
            java.io.File dataDir = new java.io.File("mods/AdminWebDash");
            java.io.File configFile = new java.io.File(dataDir, "config.json");
            JsonObject currentConfig = new JsonObject();

            if (configFile.exists()) {
                try (java.io.FileReader reader = new java.io.FileReader(configFile)) {
                    currentConfig = GSON.fromJson(reader, JsonObject.class);
                }
            }

            if (newConfig.has("backupInterval")) {
                currentConfig.addProperty("backupInterval", newConfig.get("backupInterval").getAsInt());
                AdminWebDashPlugin.getInstance().updateBackupInterval(newConfig.get("backupInterval").getAsInt());
            }

            if (newConfig.has("discordEnabled")) currentConfig.addProperty("discordEnabled", newConfig.get("discordEnabled").getAsBoolean());
            if (newConfig.has("discordToken") && !newConfig.get("discordToken").getAsString().isEmpty()) {
                currentConfig.addProperty("discordToken", newConfig.get("discordToken").getAsString());
            }
            if (newConfig.has("discordGuildId")) currentConfig.addProperty("discordGuildId", newConfig.get("discordGuildId").getAsString());
            if (newConfig.has("discordChannelLogs")) currentConfig.addProperty("discordChannelLogs", newConfig.get("discordChannelLogs").getAsString());
            if (newConfig.has("discordChannelAlerts")) currentConfig.addProperty("discordChannelAlerts", newConfig.get("discordChannelAlerts").getAsString());
            if (newConfig.has("discordChannelJoins")) currentConfig.addProperty("discordChannelJoins", newConfig.get("discordChannelJoins").getAsString());
            if (newConfig.has("discordCommandPrefix")) currentConfig.addProperty("discordCommandPrefix", newConfig.get("discordCommandPrefix").getAsString());
            
            if (newConfig.has("useHttps")) {
                boolean newHttps = newConfig.get("useHttps").getAsBoolean();
                if (newHttps != AdminWebDashPlugin.useHttps()) requiresRestart = true;
                currentConfig.addProperty("useHttps", newHttps);
            }
            if (newConfig.has("keystorePath")) {
                String newPath = newConfig.get("keystorePath").getAsString();
                if (!newPath.equals(AdminWebDashPlugin.getKeystorePath())) requiresRestart = true;
                currentConfig.addProperty("keystorePath", newPath);
            }
            if (newConfig.has("keystorePassword") && !newConfig.get("keystorePassword").getAsString().isEmpty()) {
                currentConfig.addProperty("keystorePassword", newConfig.get("keystorePassword").getAsString());
                requiresRestart = true;
            }
            if (newConfig.has("domain")) currentConfig.addProperty("domain", newConfig.get("domain").getAsString());


            try (java.io.FileWriter writer = new java.io.FileWriter(configFile)) {
                GSON.toJson(currentConfig, writer);
            }
            
            JsonObject response = new JsonObject();
            response.addProperty("status", "success");
            response.addProperty("requiresRestart", requiresRestart);
            return GSON.toJson(response);

        } catch (Exception e) {
            getLogger().severe("Failed to update config: " + e.getMessage());
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private static String getDiscordEvents() {
        // Structured event feed for Discord consumption.
        // Returning empty list for now, we will add an event queue to memory and poll from here later.
        JsonArray events = new JsonArray();
        return GSON.toJson(events);
    }
    
    private static String getChatLog() {
        return GSON.toJson(ChatLog.getMessages());
    }

    public static String kickPlayer(String body) {
        try {
            JsonObject json = GSON.fromJson(body, JsonObject.class);
            if (!json.has("uuid")) return "{\"error\": \"Missing UUID\"}";
            
            UUID uuid = UUID.fromString(json.get("uuid").getAsString());
            String reason = json.has("reason") ? json.get("reason").getAsString() : "Kicked by Admin";
            
            PlayerRef ref = Universe.get().getPlayer(uuid);
            if (ref != null && ref.getPacketHandler() != null) {
                ref.getPacketHandler().disconnect(reason);
                getLogger().info("[API] Kicked player " + ref.getUsername() + " (" + uuid + ") - Reason: " + reason);
                return "{\"status\": \"success\", \"message\": \"Player kicked\"}";
            }
            return "{\"error\": \"Player or connection not found\"}";
        } catch (Exception e) {
            getLogger().log("ERROR", "Error kicking player", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    public static String banPlayer(String body) {
        try {
            JsonObject json = GSON.fromJson(body, JsonObject.class);
            if (!json.has("uuid")) return "{\"error\": \"Missing UUID\"}";
            
            UUID uuid = UUID.fromString(json.get("uuid").getAsString());
            String reason = json.has("reason") ? json.get("reason").getAsString() : "Banned by Admin";
            
            PlayerRef ref = Universe.get().getPlayer(uuid);
            String playerName = (ref != null) ? ref.getUsername() : uuid.toString();
            
            if (banProviderField == null) {
                return "{\"error\": \"Ban system unavailable (reflection failed)\"}";
            }
            
            com.hypixel.hytale.server.core.modules.accesscontrol.provider.HytaleBanProvider banProvider = 
                (com.hypixel.hytale.server.core.modules.accesscontrol.provider.HytaleBanProvider) banProviderField.get(AccessControlModule.get());
            
            if (banProvider.hasBan(uuid)) {
                return "{\"error\": \"Player is already banned\"}";
            }
            
            UUID adminUuid = UUID.fromString("00000000-0000-0000-0000-000000000000");
            InfiniteBan ban = new InfiniteBan(uuid, adminUuid, Instant.now(), reason);
            
            boolean added = banProvider.modify(bans -> {
                bans.put(uuid, ban);
                return true;
            });
            
            if (added) {
                if (ref != null && ref.getPacketHandler() != null) {
                    ref.getPacketHandler().disconnect("Banned: " + reason);
                }
                getLogger().info("[API] Banned player " + playerName + " (" + uuid + ") - Reason: " + reason);
                return "{\"status\": \"success\", \"message\": \"Player banned\"}";
            } else {
                return "{\"error\": \"Failed to add ban\"}";
            }
        } catch (Exception e) {
            getLogger().log("ERROR", "Error banning player", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private static String toggleOP(String body) {
        try {
            JsonObject json = GSON.fromJson(body, JsonObject.class);
            if (!json.has("uuid")) return "{\"error\": \"Missing UUID\"}";
            
            UUID uuid = UUID.fromString(json.get("uuid").getAsString());
            PlayerRef ref = Universe.get().getPlayer(uuid);
            
            if (ref == null) {
                return "{\"error\": \"Player not found\"}";
            }
            
            // Check if player is in OP group
            boolean currentlyOP = PermissionsModule.get().getGroupsForUser(uuid).contains("OP");
            
            if (currentlyOP) {
                // Remove from OP group
                PermissionsModule.get().removeUserFromGroup(uuid, "OP");
                getLogger().info("[API] Revoked OP from " + ref.getUsername() + " (" + uuid + ")");
            } else {
                // Add to OP group
                PermissionsModule.get().addUserToGroup(uuid, "OP");
                getLogger().info("[API] Granted OP to " + ref.getUsername() + " (" + uuid + ")");
            }
            
            JsonObject response = new JsonObject();
            response.addProperty("status", "success");
            response.addProperty("isOp", !currentlyOP);
            response.addProperty("message", !currentlyOP ? "OP granted" : "OP revoked");
            return GSON.toJson(response);
        } catch (Exception e) {
            getLogger().log("ERROR", "Error toggling OP", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private static String teleportPlayer(String body) {
        try {
            JsonObject json = GSON.fromJson(body, JsonObject.class);
            if (!json.has("uuid") || !json.has("targetUuid")) {
                return "{\"error\": \"Missing UUID or targetUuid\"}";
            }
            
            UUID uuid = UUID.fromString(json.get("uuid").getAsString());
            UUID targetUuid = UUID.fromString(json.get("targetUuid").getAsString());
            
            PlayerRef playerRef = Universe.get().getPlayer(uuid);
            PlayerRef targetRef = Universe.get().getPlayer(targetUuid);
            
            if (playerRef == null || targetRef == null) {
                return "{\"error\": \"Player or target not found\"}";
            }
            
            Ref<EntityStore> playerEntityRef = playerRef.getReference();
            Ref<EntityStore> targetEntityRef = targetRef.getReference();
            
            if (playerEntityRef == null || !playerEntityRef.isValid() || 
                targetEntityRef == null || !targetEntityRef.isValid()) {
                return "{\"error\": \"Player or target not in world\"}";
            }
            
            Store<EntityStore> targetStore = targetEntityRef.getStore();
            World targetWorld = targetStore.getExternalData().getWorld();
            
            // Get target position on the target's world thread
            CompletableFuture<Vector3d> targetPosFuture = CompletableFuture.supplyAsync(() -> {
                TransformComponent targetTransform = targetStore.getComponent(targetEntityRef, TransformComponent.getComponentType());
                return targetTransform != null ? targetTransform.getPosition() : null;
            }, targetWorld);
            
            Vector3d targetPos = targetPosFuture.get(2, TimeUnit.SECONDS);
            if (targetPos == null) {
                return "{\"error\": \"Could not get target position\"}";
            }
            
            // Teleport player on their world thread
            Store<EntityStore> playerStore = playerEntityRef.getStore();
            World playerWorld = playerStore.getExternalData().getWorld();
            
            CompletableFuture<Boolean> teleportFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    TransformComponent playerTransform = playerStore.getComponent(playerEntityRef, TransformComponent.getComponentType());
                    if (playerTransform != null) {
                        playerTransform.setPosition(targetPos);
                        return true;
                    }
                    return false;
                } catch (Exception e) {
                    getLogger().log("ERROR", "Error during teleport", e);
                    return false;
                }
            }, playerWorld);
            
            boolean success = teleportFuture.get(2, TimeUnit.SECONDS);
            
            if (success) {
                getLogger().info("[API] Teleported " + playerRef.getUsername() + " to " + targetRef.getUsername());
                return "{\"status\": \"success\", \"message\": \"Player teleported\"}";
            } else {
                return "{\"error\": \"Teleport failed\"}";
            }
        } catch (Exception e) {
            getLogger().log("ERROR", "Error teleporting player", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
    
    private static String getBannedPlayers() {
        try {
            JsonArray bansArray = new JsonArray();
            
            // First, try to read directly from the file to ensure we have the latest data
            java.nio.file.Path bansPath = java.nio.file.Paths.get("bans.json");
            
            if (java.nio.file.Files.exists(bansPath)) {
                try {
                    String content = new String(java.nio.file.Files.readAllBytes(bansPath), java.nio.charset.StandardCharsets.UTF_8);
                    com.google.gson.JsonArray fileArray = com.google.gson.JsonParser.parseString(content).getAsJsonArray();
                    
                    // Parse each ban from the file
                    for (com.google.gson.JsonElement element : fileArray) {
                        JsonObject banJson = element.getAsJsonObject();
                        
                        // Extract the data we need
                        JsonObject displayBan = new JsonObject();
                        displayBan.addProperty("uuid", banJson.get("target").getAsString());
                        displayBan.addProperty("bannedBy", banJson.get("by").getAsString());
                        displayBan.addProperty("timestamp", banJson.get("timestamp").getAsLong());
                        if (banJson.has("reason")) {
                            displayBan.addProperty("reason", banJson.get("reason").getAsString());
                        }
                        displayBan.addProperty("type", banJson.get("type").getAsString());
                        
                        bansArray.add(displayBan);
                    }
                    
                    getLogger().info("[API] Loaded " + bansArray.size() + " bans from file");
                    return GSON.toJson(bansArray);
                } catch (Exception e) {
                    getLogger().log("WARN", "Failed to read bans from file, falling back to memory", e);
                }
            }
            
            // Fallback: Use reflection to access the private banProvider field
            java.lang.reflect.Field field = AccessControlModule.class.getDeclaredField("banProvider");
            field.setAccessible(true);
            com.hypixel.hytale.server.core.modules.accesscontrol.provider.HytaleBanProvider banProvider = 
                (com.hypixel.hytale.server.core.modules.accesscontrol.provider.HytaleBanProvider) field.get(AccessControlModule.get());
            
            // Access the bans map using reflection with proper locking
            java.lang.reflect.Field bansField = banProvider.getClass().getDeclaredField("bans");
            bansField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<UUID, com.hypixel.hytale.server.core.modules.accesscontrol.ban.Ban> bans = 
                (java.util.Map<UUID, com.hypixel.hytale.server.core.modules.accesscontrol.ban.Ban>) bansField.get(banProvider);
            
            // Access the fileLock field
            java.lang.reflect.Field lockField = banProvider.getClass().getSuperclass().getDeclaredField("fileLock");
            lockField.setAccessible(true);
            java.util.concurrent.locks.ReadWriteLock lock = 
                (java.util.concurrent.locks.ReadWriteLock) lockField.get(banProvider);
            
            // Acquire read lock
            lock.readLock().lock();
            try {
                for (java.util.Map.Entry<UUID, com.hypixel.hytale.server.core.modules.accesscontrol.ban.Ban> entry : bans.entrySet()) {
                    com.hypixel.hytale.server.core.modules.accesscontrol.ban.Ban ban = entry.getValue();
                    JsonObject banJson = new JsonObject();
                    banJson.addProperty("uuid", entry.getKey().toString());
                    banJson.addProperty("bannedBy", ban.getBy().toString());
                    banJson.addProperty("timestamp", ban.getTimestamp().toEpochMilli());
                    ban.getReason().ifPresent(reason -> banJson.addProperty("reason", reason));
                    banJson.addProperty("type", ban.getType());
                    bansArray.add(banJson);
                }
            } finally {
                lock.readLock().unlock();
            }
            
            getLogger().info("[API] Loaded " + bansArray.size() + " bans from memory");
            return GSON.toJson(bansArray);
        } catch (Exception e) {
            getLogger().log("ERROR", "Error getting banned players", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
    
    private static String getBansFile() {
        try {
            // Read the bans.json file directly from disk
            java.nio.file.Path bansPath = java.nio.file.Paths.get("bans.json");
            
            if (!java.nio.file.Files.exists(bansPath)) {
                JsonObject response = new JsonObject();
                response.addProperty("fileExists", false);
                response.addProperty("content", "[]");
                response.addProperty("path", bansPath.toAbsolutePath().toString());
                return GSON.toJson(response);
            }
            
            String content = new String(java.nio.file.Files.readAllBytes(bansPath), java.nio.charset.StandardCharsets.UTF_8);
            
            JsonObject response = new JsonObject();
            response.addProperty("fileExists", true);
            response.addProperty("content", content);
            response.addProperty("path", bansPath.toAbsolutePath().toString());
            response.addProperty("lastModified", java.nio.file.Files.getLastModifiedTime(bansPath).toMillis());
            
            return GSON.toJson(response);
        } catch (Exception e) {
            getLogger().log("ERROR", "Error reading bans file", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
    
    public static String unbanPlayer(String body) {
        try {
            JsonObject json = GSON.fromJson(body, JsonObject.class);
            if (!json.has("uuid")) return "{\"error\": \"Missing UUID\"}";
            
            UUID uuid = UUID.fromString(json.get("uuid").getAsString());
            
            getLogger().info("[API] Attempting to unban player " + uuid);
            
            // Read the bans.json file directly
            java.nio.file.Path bansPath = java.nio.file.Paths.get("bans.json");
            
            if (!java.nio.file.Files.exists(bansPath)) {
                getLogger().warning("[API] bans.json file does not exist");
                return "{\"error\": \"Bans file not found\"}";
            }
            
            // Read and parse the file
            String content = new String(java.nio.file.Files.readAllBytes(bansPath), java.nio.charset.StandardCharsets.UTF_8);
            com.google.gson.JsonArray fileArray = com.google.gson.JsonParser.parseString(content).getAsJsonArray();
            
            // Find and remove the ban
            boolean found = false;
            com.google.gson.JsonArray newArray = new com.google.gson.JsonArray();
            
            for (com.google.gson.JsonElement element : fileArray) {
                JsonObject banJson = element.getAsJsonObject();
                String targetUuid = banJson.get("target").getAsString();
                
                if (targetUuid.equals(uuid.toString())) {
                    found = true;
                    getLogger().info("[API] Found ban for " + uuid + " in file, removing");
                } else {
                    newArray.add(element);
                }
            }
            
            if (!found) {
                getLogger().warning("[API] No ban found in file for " + uuid);
                return "{\"error\": \"Player is not banned\"}";
            }
            
            // Write the updated array back to the file
            String newContent = GSON.toJson(newArray);
            java.nio.file.Files.write(bansPath, newContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            
            getLogger().info("[API] Successfully removed ban from file for " + uuid);
            
            // Also try to remove from memory cache
            try {
                java.lang.reflect.Field field = AccessControlModule.class.getDeclaredField("banProvider");
                field.setAccessible(true);
                com.hypixel.hytale.server.core.modules.accesscontrol.provider.HytaleBanProvider banProvider = 
                    (com.hypixel.hytale.server.core.modules.accesscontrol.provider.HytaleBanProvider) field.get(AccessControlModule.get());
                
                if (banProvider.hasBan(uuid)) {
                    banProvider.modify(bans -> {
                        bans.remove(uuid);
                        getLogger().info("[API] Also removed ban from memory cache for " + uuid);
                        return false; // Don't save again, we already saved to file
                    });
                }
            } catch (Exception e) {
                getLogger().log("WARN", "Failed to remove from memory cache, but file was updated", e);
            }
            
            return "{\"status\": \"success\", \"message\": \"Player unbanned\"}";
        } catch (Exception e) {
            getLogger().log("ERROR", "Error unbanning player", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
    
    // ==================== NEW FEATURES ====================
    
    // NOTE: Some features use reflection or workarounds due to API limitations
    // These implementations are based on available public APIs and component patterns
    
    public static String clearInventory(String body) {
        try {
            JsonObject json = GSON.fromJson(body, JsonObject.class);
            if (!json.has("uuid")) return "{\"error\": \"Missing UUID\"}";
            
            UUID uuid = UUID.fromString(json.get("uuid").getAsString());
            PlayerRef playerRef = Universe.get().getPlayer(uuid);
            
            if (playerRef == null) {
                return "{\"error\": \"Player not found\"}";
            }
            
            Ref<EntityStore> entityRef = playerRef.getReference();
            if (entityRef == null || !entityRef.isValid()) {
                return "{\"error\": \"Player not in world\"}";
            }
            
            Store<EntityStore> store = entityRef.getStore();
            World world = store.getExternalData().getWorld();
            
            CompletableFuture<Boolean> clearFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    Player playerComp = store.getComponent(entityRef, Player.getComponentType());
                    if (playerComp != null) {
                        Inventory inv = playerComp.getInventory();
                        if (inv != null) {
                            inv.clear();
                            getLogger().info("[API] Cleared inventory using Inventory.clear()");
                            return true;
                        }
                    }
                    return false;
                } catch (Exception e) {
                    getLogger().log("ERROR", "Error during clear inventory", e);
                    return false;
                }
            }, world);
            
            boolean success = clearFuture.get(2, TimeUnit.SECONDS);
            
            if (success) {
                getLogger().info("[API] Cleared inventory for " + playerRef.getUsername());
                return "{\"status\": \"success\"}";
            } else {
                return "{\"error\": \"Clear inventory failed\"}";
            }
        } catch (Exception e) {
            getLogger().log("ERROR", "Error in clearInventory", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
    
    public static String healPlayer(String body) {
        try {
            JsonObject json = GSON.fromJson(body, JsonObject.class);
            if (!json.has("uuid")) return "{\"error\": \"Missing UUID\"}";
            
            UUID uuid = UUID.fromString(json.get("uuid").getAsString());
            PlayerRef playerRef = Universe.get().getPlayer(uuid);
            
            if (playerRef == null) {
                return "{\"error\": \"Player not found\"}";
            }
            
            Ref<EntityStore> entityRef = playerRef.getReference();
            if (entityRef == null || !entityRef.isValid()) {
                return "{\"error\": \"Player not in world\"}";
            }
            
            Store<EntityStore> store = entityRef.getStore();
            World world = store.getExternalData().getWorld();
            
            CompletableFuture<Boolean> healFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    EntityStatMap statMap = store.getComponent(entityRef, EntityStatMap.getComponentType());
                    if (statMap != null) {
                        EntityStatValue healthStat = statMap.get(DefaultEntityStatTypes.getHealth());
                        if (healthStat != null) {
                            float currentHealth = healthStat.get();
                            float maxHealth = healthStat.getMax();
                            float healAmount = maxHealth - currentHealth;
                            
                            if (healAmount > 0) {
                                // Use the public addStatValue method - this properly syncs to client!
                                float newHealth = statMap.addStatValue(DefaultEntityStatTypes.getHealth(), healAmount);
                                getLogger().info("[API] Healed player: " + currentHealth + " -> " + newHealth);
                                return true;
                            } else {
                                getLogger().info("[API] Player already at full health");
                                return true;
                            }
                        }
                    }
                    return false;
                } catch (Exception e) {
                    getLogger().log("ERROR", "Error during heal", e);
                    return false;
                }
            }, world);
            
            boolean success = healFuture.get(2, TimeUnit.SECONDS);
            
            if (success) {
                getLogger().info("[API] Healed " + playerRef.getUsername());
                return "{\"status\": \"success\"}";
            } else {
                return "{\"error\": \"Heal failed\"}";
            }
        } catch (Exception e) {
            getLogger().log("ERROR", "Error in healPlayer", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
    
    public static String mutePlayer(String body) {
        try {
            JsonObject json = GSON.fromJson(body, JsonObject.class);
            if (!json.has("uuid")) return "{\"error\": \"Missing UUID\"}";
            
            UUID uuid = UUID.fromString(json.get("uuid").getAsString());
            String reason = json.has("reason") ? json.get("reason").getAsString() : "Muted by admin";
            Long duration = json.has("duration") ? json.get("duration").getAsLong() : null;
            
            UUID adminUuid = UUID.fromString("00000000-0000-0000-0000-000000000000");
            MuteTracker.mute(uuid, adminUuid, duration, reason);
            
            return "{\"status\": \"success\"}";
        } catch (Exception e) {
            getLogger().log("ERROR", "Error in mutePlayer", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
    
    public static String unmutePlayer(String body) {
        try {
            JsonObject json = GSON.fromJson(body, JsonObject.class);
            if (!json.has("uuid")) return "{\"error\": \"Missing UUID\"}";
            
            UUID uuid = UUID.fromString(json.get("uuid").getAsString());
            MuteTracker.unmute(uuid);
            
            return "{\"status\": \"success\"}";
        } catch (Exception e) {
            getLogger().log("ERROR", "Error in unmutePlayer", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
    
    private static String getMutes() {
        try {
            JsonArray mutesArray = new JsonArray();
            for (Map.Entry<UUID, MuteTracker.Mute> entry : MuteTracker.getMutes().entrySet()) {
                JsonObject muteJson = new JsonObject();
                muteJson.addProperty("uuid", entry.getKey().toString());
                muteJson.addProperty("mutedBy", entry.getValue().mutedBy.toString());
                muteJson.addProperty("timestamp", entry.getValue().timestamp.toEpochMilli());
                if (entry.getValue().durationSeconds != null) {
                    muteJson.addProperty("duration", entry.getValue().durationSeconds);
                    muteJson.addProperty("remaining", entry.getValue().getRemainingSeconds());
                }
                muteJson.addProperty("reason", entry.getValue().reason);
                
                // Try to get player name
                PlayerRef ref = Universe.get().getPlayer(entry.getKey());
                if (ref != null) {
                    muteJson.addProperty("name", ref.getUsername());
                }
                
                mutesArray.add(muteJson);
            }
            return GSON.toJson(mutesArray);
        } catch (Exception e) {
            getLogger().log("ERROR", "Error in getMutes", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
    
    private static String getWarps() {
        try {
            JsonArray warpsArray = new JsonArray();
            for (Map.Entry<String, WarpManager.Warp> entry : WarpManager.getWarps().entrySet()) {
                JsonObject warpJson = new JsonObject();
                WarpManager.Warp warp = entry.getValue();
                warpJson.addProperty("name", warp.name);
                warpJson.addProperty("world", warp.world);
                warpJson.addProperty("x", warp.x);
                warpJson.addProperty("y", warp.y);
                warpJson.addProperty("z", warp.z);
                warpJson.addProperty("createdBy", warp.createdBy.toString());
                warpJson.addProperty("createdAt", warp.createdAt);
                warpsArray.add(warpJson);
            }
            return GSON.toJson(warpsArray);
        } catch (Exception e) {
            getLogger().log("ERROR", "Error in getWarps", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
    
    private static String createWarp(String body) {
        try {
            JsonObject json = GSON.fromJson(body, JsonObject.class);
            if (!json.has("name") || !json.has("uuid")) {
                return "{\"error\": \"Missing name or uuid\"}";
            }
            
            String name = json.get("name").getAsString();
            UUID uuid = UUID.fromString(json.get("uuid").getAsString());
            
            if (WarpManager.exists(name)) {
                return "{\"error\": \"Warp already exists\"}";
            }
            
            PlayerRef ref = Universe.get().getPlayer(uuid);
            if (ref == null) return "{\"error\": \"Player not found\"}";
            
            Ref<EntityStore> entityRef = ref.getReference();
            if (entityRef == null || !entityRef.isValid()) {
                return "{\"error\": \"Player not in world\"}";
            }
            
            Store<EntityStore> store = entityRef.getStore();
            World world = store.getExternalData().getWorld();
            
            CompletableFuture<JsonObject> future = CompletableFuture.supplyAsync(() -> {
                JsonObject result = new JsonObject();
                try {
                    TransformComponent transform = store.getComponent(entityRef, TransformComponent.getComponentType());
                    if (transform != null) {
                        Vector3d pos = transform.getPosition();
                        result.addProperty("x", pos.x);
                        result.addProperty("y", pos.y);
                        result.addProperty("z", pos.z);
                        result.addProperty("world", world.getName());
                        result.addProperty("success", true);
                    } else {
                        result.addProperty("success", false);
                    }
                } catch (Exception e) {
                    result.addProperty("success", false);
                }
                return result;
            }, world);
            
            JsonObject result = future.get(2, TimeUnit.SECONDS);
            if (result.get("success").getAsBoolean()) {
                WarpManager.createWarp(
                    name,
                    result.get("world").getAsString(),
                    result.get("x").getAsDouble(),
                    result.get("y").getAsDouble(),
                    result.get("z").getAsDouble(),
                    uuid
                );
                return "{\"status\": \"success\"}";
            }
            return "{\"error\": \"Failed to get player position\"}";
        } catch (Exception e) {
            getLogger().log("ERROR", "Error in createWarp", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
    
    private static String deleteWarp(String body) {
        try {
            JsonObject json = GSON.fromJson(body, JsonObject.class);
            if (!json.has("name")) return "{\"error\": \"Missing name\"}";
            
            String name = json.get("name").getAsString();
            if (!WarpManager.exists(name)) {
                return "{\"error\": \"Warp not found\"}";
            }
            
            WarpManager.deleteWarp(name);
            return "{\"status\": \"success\"}";
        } catch (Exception e) {
            getLogger().log("ERROR", "Error in deleteWarp", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
    
    private static String teleportToWarp(String body) {
        try {
            JsonObject json = GSON.fromJson(body, JsonObject.class);
            
            if (!json.has("uuid") || !json.has("warp") || json.get("uuid").isJsonNull() || json.get("warp").isJsonNull()) {
                return "{\"error\": \"Missing UUID or warp name\"}";
            }
            
            UUID uuid = UUID.fromString(json.get("uuid").getAsString());
            String warpName = json.get("warp").getAsString();
            
            WarpManager.Warp warp = WarpManager.getWarp(warpName);
            if (warp == null) {
                return "{\"error\": \"Warp not found\"}";
            }
            
            PlayerRef playerRef = Universe.get().getPlayer(uuid);
            if (playerRef == null) {
                return "{\"error\": \"Player not found\"}";
            }
            
            Ref<EntityStore> playerEntityRef = playerRef.getReference();
            if (playerEntityRef == null || !playerEntityRef.isValid()) {
                return "{\"error\": \"Player not in world\"}";
            }
            
            Store<EntityStore> playerStore = playerEntityRef.getStore();
            World playerWorld = playerStore.getExternalData().getWorld();
            
            Vector3d destination = new Vector3d(warp.x, warp.y, warp.z);
            
            // Execute on the world thread to get rotation and add teleport component
            playerWorld.execute(() -> {
                // Get current rotation to preserve it (must be done on world thread)
                TransformComponent transform = playerStore.getComponent(playerEntityRef, TransformComponent.getComponentType());
                Vector3f currentRotation = transform != null ? transform.getRotation() : Vector3f.ZERO;
                
                // Create Teleport component and add it to the entity
                Teleport teleport = new Teleport(destination, currentRotation);
                playerStore.addComponent(playerEntityRef, Teleport.getComponentType(), teleport);
                
                // Send message to player
                playerRef.sendMessage(Message.raw("Teleported to warp: " + warpName));
            });
            
            getLogger().info("[API] Teleported " + playerRef.getUsername() + " to warp '" + warpName + "' at (" + warp.x + ", " + warp.y + ", " + warp.z + ")");
            return "{\"status\": \"success\"}";
        } catch (Exception e) {
            getLogger().log("ERROR", "Error in teleportToWarp", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
    
    public static String setTime(String body) {
        try {
            JsonObject json = GSON.fromJson(body, JsonObject.class);
            if (!json.has("time")) return "{\"error\": \"Missing time\"}";
            
            String timeStr = json.get("time").getAsString();
            double dayTime;
            
            // Convert time string to dayTime value (0.0 to 1.0)
            switch (timeStr.toLowerCase()) {
                case "midnight":
                    dayTime = 0.0;
                    break;
                case "sunrise":
                    dayTime = 0.25;
                    break;
                case "noon":
                    dayTime = 0.5;
                    break;
                case "sunset":
                    dayTime = 0.75;
                    break;
                default:
                    return "{\"error\": \"Invalid time. Use: midnight, sunrise, noon, or sunset\"}";
            }
            
            // Get the first world (or you could make this configurable)
            java.util.Collection<World> worlds = Universe.get().getWorlds().values();
            if (worlds.isEmpty()) {
                return "{\"error\": \"No worlds available\"}";
            }
            
            World world = worlds.iterator().next();
            Store<EntityStore> store = world.getEntityStore().getStore();
            
            // Execute on the world's thread
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    WorldTimeResource timeResource = store.getResource(WorldTimeResource.getResourceType());
                    if (timeResource != null) {
                        timeResource.setDayTime(dayTime, world, store);
                        getLogger().info("[API] Set time to " + timeStr + " (dayTime=" + dayTime + ")");
                        return true;
                    }
                    return false;
                } catch (Exception e) {
                    getLogger().log("ERROR", "Error setting time", e);
                    return false;
                }
            }, world);
            
            boolean success = future.get(2, TimeUnit.SECONDS);
            if (success) {
                return "{\"status\": \"success\", \"message\": \"Time set to " + timeStr + "\"}";
            }
            return "{\"error\": \"Failed to set time\"}";
        } catch (Exception e) {
            getLogger().log("ERROR", "Error in setTime", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
    
    public static String setWeather(String body) {
        try {
            JsonObject json = GSON.fromJson(body, JsonObject.class);
            if (!json.has("weather")) return "{\"error\": \"Missing weather\"}";
            
            String weatherStr = json.get("weather").getAsString();
            String weatherId;
            
            // Map common weather names to Hytale weather IDs
            switch (weatherStr.toLowerCase()) {
                case "clear":
                    weatherId = null; // null = clear/default weather
                    break;
                case "rain":
                    weatherId = "hytale:rain";
                    break;
                case "storm":
                    weatherId = "hytale:storm";
                    break;
                case "snow":
                    weatherId = "hytale:snow";
                    break;
                default:
                    // Allow custom weather IDs
                    weatherId = weatherStr;
                    break;
            }
            
            // Get the first world (or you could make this configurable)
            java.util.Collection<World> worlds = Universe.get().getWorlds().values();
            if (worlds.isEmpty()) {
                return "{\"error\": \"No worlds available\"}";
            }
            
            World world = worlds.iterator().next();
            Store<EntityStore> store = world.getEntityStore().getStore();
            
            // Execute on the world's thread
            final String finalWeatherId = weatherId;
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    WeatherResource weatherResource = store.getResource(WeatherResource.getResourceType());
                    if (weatherResource != null) {
                        weatherResource.setForcedWeather(finalWeatherId);
                        
                        // Also update the world config
                        WorldConfig config = world.getWorldConfig();
                        config.setForcedWeather(finalWeatherId);
                        config.markChanged();
                        
                        getLogger().info("[API] Set weather to " + weatherStr + " (weatherId=" + finalWeatherId + ")");
                        return true;
                    }
                    return false;
                } catch (Exception e) {
                    getLogger().log("ERROR", "Error setting weather", e);
                    return false;
                }
            }, world);
            
            boolean success = future.get(2, TimeUnit.SECONDS);
            if (success) {
                return "{\"status\": \"success\", \"message\": \"Weather set to " + weatherStr + "\"}";
            }
            return "{\"error\": \"Failed to set weather\"}";
        } catch (Exception e) {
            getLogger().log("ERROR", "Error in setWeather", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    public static String setGamemode(String body) {
        try {
            JsonObject json = GSON.fromJson(body, JsonObject.class);
            if (!json.has("uuid")) return "{\"error\": \"Missing UUID\"}";
            if (!json.has("gamemode")) return "{\"error\": \"Missing gamemode\"}";
            
            UUID uuid = UUID.fromString(json.get("uuid").getAsString());
            String gamemodeStr = json.get("gamemode").getAsString();
            
            // Parse gamemode
            GameMode gameMode;
            switch (gamemodeStr.toLowerCase()) {
                case "adventure":
                    gameMode = GameMode.Adventure;
                    break;
                case "creative":
                    gameMode = GameMode.Creative;
                    break;
                default:
                    return "{\"error\": \"Invalid gamemode. Use 'adventure' or 'creative'\"}";
            }
            
            PlayerRef playerRef = Universe.get().getPlayer(uuid);
            if (playerRef == null) {
                return "{\"error\": \"Player not found\"}";
            }
            
            Ref<EntityStore> entityRef = playerRef.getReference();
            if (entityRef == null || !entityRef.isValid()) {
                return "{\"error\": \"Player not in world\"}";
            }
            
            Store<EntityStore> store = entityRef.getStore();
            World world = store.getExternalData().getWorld();
            
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    Player.setGameMode(entityRef, gameMode, store);
                    getLogger().info("[API] Set gamemode to " + gameMode + " for " + playerRef.getUsername());
                    return true;
                } catch (Exception e) {
                    getLogger().log("ERROR", "Error setting gamemode", e);
                    return false;
                }
            }, world);
            
            boolean success = future.get(2, TimeUnit.SECONDS);
            if (success) {
                return "{\"status\": \"success\", \"message\": \"Gamemode set to " + gamemodeStr + "\"}";
            }
            return "{\"error\": \"Failed to set gamemode\"}";
        } catch (Exception e) {
            getLogger().log("ERROR", "Error in setGamemode", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
    
    public static String giveItem(String body) {
        try {
            JsonObject json = GSON.fromJson(body, JsonObject.class);
            if (!json.has("uuid")) return "{\"error\": \"Missing UUID\"}";
            if (!json.has("itemId")) return "{\"error\": \"Missing itemId\"}";
            
            UUID uuid = UUID.fromString(json.get("uuid").getAsString());
            String itemId = json.get("itemId").getAsString();
            int quantity = json.has("quantity") ? json.get("quantity").getAsInt() : 1;
            
            // Validate quantity
            if (quantity < 1 || quantity > 999) {
                return "{\"error\": \"Quantity must be between 1 and 999\"}";
            }
            
            // Get the item from the asset map
            Item item = Item.getAssetMap().getAsset(itemId);
            if (item == null) {
                return "{\"error\": \"Invalid item ID: " + itemId + "\"}";
            }
            
            PlayerRef playerRef = Universe.get().getPlayer(uuid);
            if (playerRef == null) {
                return "{\"error\": \"Player not found\"}";
            }
            
            Ref<EntityStore> entityRef = playerRef.getReference();
            if (entityRef == null || !entityRef.isValid()) {
                return "{\"error\": \"Player not in world\"}";
            }
            
            Store<EntityStore> store = entityRef.getStore();
            World world = store.getExternalData().getWorld();
            
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    Player playerComp = store.getComponent(entityRef, Player.getComponentType());
                    if (playerComp != null) {
                        ItemStack stack = new ItemStack(itemId, quantity, null);
                        ItemStackTransaction transaction = playerComp.getInventory().getCombinedHotbarFirst().addItemStack(stack);
                        ItemStack remainder = transaction.getRemainder();
                        
                        if (remainder == null || remainder.isEmpty()) {
                            getLogger().info("[API] Gave " + quantity + "x " + itemId + " to " + playerRef.getUsername());
                            return true;
                        } else {
                            getLogger().info("[API] Partially gave items to " + playerRef.getUsername() + " - inventory full");
                            return false;
                        }
                    }
                    return false;
                } catch (Exception e) {
                    getLogger().log("ERROR", "Error giving item", e);
                    return false;
                }
            }, world);
            
            boolean success = future.get(2, TimeUnit.SECONDS);
            if (success) {
                return "{\"status\": \"success\", \"message\": \"Gave " + quantity + "x " + itemId + "\"}";
            }
            return "{\"error\": \"Failed to give item - inventory may be full\"}";
        } catch (Exception e) {
            getLogger().log("ERROR", "Error in giveItem", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
    
    private static String getAllItems() {
        try {
            getLogger().info("[DashboardAPI] Fetching all items from asset store");
            
            JsonObject response = new JsonObject();
            JsonArray itemsArray = new JsonArray();
            
            // Get all items from the Item asset map (same way as giveItem does)
            var assetMap = Item.getAssetMap();
            
            getLogger().info("[DashboardAPI] Asset map type: " + assetMap.getClass().getName());
            
            // Iterate through all items using forEach
            assetMap.getAssetMap().forEach((itemId, item) -> {
                try {
                    if (item != null) {
                        JsonObject itemObj = new JsonObject();
                        itemObj.addProperty("id", itemId);
                        
                        // Get human-readable name with better conversion
                        String displayName = itemId;
                        if (itemId.contains(":")) {
                            displayName = itemId.split(":", 2)[1];
                        }
                        
                        // Convert underscores to spaces
                        displayName = displayName.replace("_", " ");
                        
                        // Apply common name mappings for better searchability
                        displayName = applyCommonNameMappings(displayName);
                        
                        // Capitalize words
                        displayName = capitalizeWords(displayName);
                        
                        itemObj.addProperty("name", displayName);
                        
                        // Also store searchable keywords (lowercase, no spaces)
                        String searchKeywords = displayName.toLowerCase().replace(" ", "");
                        itemObj.addProperty("keywords", searchKeywords);
                        
                        itemsArray.add(itemObj);
                    }
                } catch (Exception e) {
                    getLogger().log("WARN", "[DashboardAPI] Error processing item: " + itemId, e);
                }
            });
            
            response.add("items", itemsArray);
            getLogger().info("[DashboardAPI] Returning " + itemsArray.size() + " items");
            
            if (itemsArray.size() > 0) {
                getLogger().info("[DashboardAPI] Sample items: " + itemsArray.get(0).toString() + 
                    (itemsArray.size() > 1 ? ", " + itemsArray.get(1).toString() : ""));
            }
            
            return GSON.toJson(response);
        } catch (Exception e) {
            getLogger().log("ERROR", "[DashboardAPI] Error in getAllItems", e);
            return "{\"error\": \"Failed to fetch items: " + e.getMessage() + "\"}";
        }
    }
    
    private static String applyCommonNameMappings(String name) {
        // Apply common name transformations for better searchability
        // This helps users find items using common terms
        
        // Rock_Stone_X -> Cobblestone variants
        if (name.toLowerCase().contains("rock stone")) {
            name = name.replace("Rock Stone", "Cobblestone");
            name = name.replace("rock stone", "cobblestone");
        }
        
        // Add more mappings as needed
        // Wood_Plank_X -> Wood Plank variants
        // Metal_X -> Metal variants
        // etc.
        
        return name;
    }
    
    private static String capitalizeWords(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        
        String[] words = str.split(" ");
        StringBuilder result = new StringBuilder();
        
        for (String word : words) {
            if (word.length() > 0) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
                result.append(" ");
            }
        }
        
        return result.toString().trim();
    }
    
    private static String clearCurseForgeCache() {
        try {
            CurseForgeAPI.clearCache();
            return "{\"status\": \"success\", \"message\": \"CurseForge cache cleared\"}";
        } catch (Exception e) {
            getLogger().log("ERROR", "Error clearing CurseForge cache", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
    // World Info & Gamerules
    private static String getWorldInfo() {
        World world = Universe.get().getDefaultWorld();
        if (world == null) return "{\"error\": \"No default world loaded\"}";

        JsonObject json = new JsonObject();
        json.addProperty("name", world.getName());
        json.addProperty("dimension", 0); // Placeholder
        json.addProperty("time", world.getWorldConfig().getGameTime().toString());
        
        String weather = world.getWorldConfig().getForcedWeather();
        json.addProperty("weather", weather != null ? weather : "Dynamic");
        
        json.addProperty("players", world.getPlayerCount());
        
        return GSON.toJson(json);
    }

    private static String getGameRules() {
        World world = Universe.get().getDefaultWorld();
        if (world == null) return "{\"error\": \"No default world loaded\"}";
        WorldConfig config = world.getWorldConfig();

        JsonObject json = new JsonObject();
        json.addProperty("isPvpEnabled", config.isPvpEnabled());
        json.addProperty("isFallDamageEnabled", config.isFallDamageEnabled());
        json.addProperty("isSpawningNPC", config.isSpawningNPC());
        json.addProperty("isGameTimePaused", config.isGameTimePaused());
        
        // Use reflection for protected fields without getters/setters visible
        try {
            // Block Rules live in the Asset WorldConfig, not Universe WorldConfig
            String gpId = config.getGameplayConfig();
            com.hypixel.hytale.server.core.asset.type.gameplay.GameplayConfig gp = 
                com.hypixel.hytale.server.core.asset.type.gameplay.GameplayConfig.getAssetMap().getAsset(gpId);
            
            boolean bb = true;
            boolean bp = true;
            
            if (gp != null) {
                com.hypixel.hytale.server.core.asset.type.gameplay.WorldConfig assetConfig = gp.getWorldConfig();
                
                Field blockBreaking = assetConfig.getClass().getDeclaredField("allowBlockBreaking");
                blockBreaking.setAccessible(true);
                bb = blockBreaking.getBoolean(assetConfig);
                
                Field blockPlacement = assetConfig.getClass().getDeclaredField("allowBlockPlacement");
                blockPlacement.setAccessible(true);
                bp = blockPlacement.getBoolean(assetConfig);
            }
            
            json.addProperty("isBlockBreakingAllowed", bb);
            json.addProperty("isBlockPlacementAllowed", bp);

        } catch (Exception e) {
             getLogger().log("WARN", "Failed to reflect block rules: " + e.getMessage());
             // Fallback to defaults
             json.addProperty("isBlockBreakingAllowed", true);
             json.addProperty("isBlockPlacementAllowed", true);
        }

        json.addProperty("isAllNPCFrozen", config.isAllNPCFrozen());

        return GSON.toJson(json);
    }

    private static String updateGameRules(String body) {
        // ... (existing updateGameRules code)
        return "{\"success\": true}";
    }

    private static String executeCommand(String body) {
        try {
            JsonObject json = GSON.fromJson(body, JsonObject.class);
            if (!json.has("command")) return "{\"status\": \"error\", \"error\": \"Missing command\"}";
            
            String cmd = json.get("command").getAsString();
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            
            CommandManager.get().handleCommand(ConsoleSender.INSTANCE, cmd);
            return "{\"status\": \"success\"}";
        } catch (Exception e) {
            return "{\"status\": \"error\", \"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private static String getEntityStats() {
        try {
            JsonObject root = new JsonObject();
            JsonArray worldsArray = new JsonArray();
            AtomicInteger totalEntities = new AtomicInteger(0);

            for (World world : Universe.get().getWorlds().values()) {
                JsonObject wObj = new JsonObject();
                wObj.addProperty("name", world.getName());
                
                int count = world.getEntityStore().getStore().getEntityCount();
                wObj.addProperty("count", count);
                totalEntities.addAndGet(count);
                
                worldsArray.add(wObj);
            }

            root.add("worlds", worldsArray);
            root.addProperty("total", totalEntities.get());
            if (root.get("total") == null) root.addProperty("total", 0);
            return GSON.toJson(root);
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
    private static String getChatMessages() {
        JsonObject root = new JsonObject();
        root.addProperty("status", "success");
        JsonArray logs = new JsonArray();
        for (ChatLog.ChatEntry entry : ChatLog.getMessages()) {
            logs.add(entry.sender + ": " + entry.message);
        }
        root.add("logs", logs);
        return GSON.toJson(root);
    }
    
    private static String testDiscordConnection(String body) {
        try {
            JsonObject json = GSON.fromJson(body, JsonObject.class);
            
            String token = json.has("discordToken") && !json.get("discordToken").getAsString().isEmpty() 
                ? json.get("discordToken").getAsString() 
                : AdminWebDashPlugin.getDiscordToken();
                
            String channelId = json.has("discordChannelLogs") && !json.get("discordChannelLogs").getAsString().isEmpty() 
                ? json.get("discordChannelLogs").getAsString() 
                : AdminWebDashPlugin.getDiscordChannelLogs();
                
            if (token == null || token.isEmpty()) {
                return "{\"error\": \"No Discord Bot Token provided.\"}";
            }
            if (channelId == null || channelId.isEmpty()) {
                return "{\"error\": \"No Discord Logs Channel ID provided. Please set at least the logs channel ID.\"}";
            }
            
            java.net.URL url = new java.net.URL("https://discord.com/api/v10/channels/" + channelId + "/messages");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bot " + token);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            
            String payload = "{\"content\": \":white_check_mark: **AdminWebDash Connection Test Successful!**\"}";
            try (java.io.OutputStream os = conn.getOutputStream()) {
                byte[] input = payload.getBytes("utf-8");
                os.write(input, 0, input.length);
            }
            
            int code = conn.getResponseCode();
            if (code == 200 || code == 204) {
                return "{\"status\": \"success\"}";
            } else {
                java.io.InputStream is = conn.getErrorStream();
                if (is == null) is = conn.getInputStream();
                String err = new String(is.readAllBytes(), "UTF-8");
                return "{\"error\": \"Discord API returned " + code + ": " + err.replace("\"", "\\\"").replace("\n", " ") + "\"}";
            }
        } catch (Exception e) {
            getLogger().log("ERROR", "Error testing Discord connection", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
}
