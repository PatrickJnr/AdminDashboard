package uk.co.grimtech.admin.web;

import uk.co.grimtech.admin.AdminDashboardPlugin;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.Level;

public class DashboardAPI {
    private static final Gson GSON = new Gson();
    private static final Logger LOGGER = Logger.getLogger("AdminDashboardAPI");

    public static String handleRequest(String path, String method, String body) {
        LOGGER.info("[API] " + method + " " + path);
        if (path.equals("/api/players")) {
            return getPlayers();
        } else if (path.startsWith("/api/avatar/")) {
            String identifier = path.substring("/api/avatar/".length());
            byte[] avatar = AvatarCache.getAvatar(identifier);
            if (avatar != null) {
                // Return base64 or similar if we want to keep handleRequest returning String,
                // or modify HytaleHttpServer to handle byte[] responses.
                // For now, let's return a special marker or base64.
                return "AVATAR_DATA:" + java.util.Base64.getEncoder().encodeToString(avatar);
            }
            return "{\"error\": \"Avatar not found\"}";
        } else if (path.startsWith("/api/player/") && path.endsWith("/inv")) {
            String uuidStr = path.substring(12, path.length() - 4);
            return getPlayerInventory(uuidStr);
        } else if (path.equals("/api/stats")) {
            return getServerStats();
        } else if (path.equals("/api/broadcast") && method.equals("POST")) {
            return broadcastMessage(body);
        } else if (path.equals("/api/plugins")) {
            return getPlugins();
        } else if (path.equals("/api/chat")) {
            return getChatLog();
        }
        
        return "{\"error\": \"Invalid endpoint\"}";
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
                                    EntityStatValue healthStat = statMap.get(DefaultEntityStatTypes.getHealth());
                                    if (healthStat != null) {
                                        playerJson.addProperty("health", healthStat.get());
                                        playerJson.addProperty("maxHealth", healthStat.getMax());
                                    }
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
                            LOGGER.log(Level.WARNING, "Error gathering dynamic data for " + ref.getUsername(), e);
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
            LOGGER.log(Level.SEVERE, "Error getting players: " + e.getMessage(), e);
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

            JsonObject invJson = new JsonObject();
            Ref<EntityStore> entityRef = ref.getReference();
            if (entityRef != null && entityRef.isValid()) {
                Player playerComp = entityRef.getStore().getComponent(entityRef, Player.getComponentType());
                if (playerComp != null) {
                    Inventory inv = playerComp.getInventory();
                    invJson.add("hotbar", serializeContainer(inv.getHotbar()));
                    invJson.add("storage", serializeContainer(inv.getStorage()));
                    invJson.add("armor", serializeContainer(inv.getArmor()));
                }
            }
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
                items.add(item);
            }
        }
        return items;
    }

    private static String getServerStats() {
        JsonObject stats = new JsonObject();
        stats.addProperty("onlinePlayers", Universe.get().getPlayers().size());
        
        // Calculate Uptime
        long uptimeMs = System.currentTimeMillis() - AdminDashboardPlugin.getStartTime();
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
            LOGGER.warning("Could not calculate actual TPS: " + e.getMessage());
        }
        stats.addProperty("tps", Math.round(tps * 10.0) / 10.0);

        // Memory usage
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        stats.addProperty("memoryUsed", usedMemory / (1024 * 1024)); // MB
        stats.addProperty("memoryMax", runtime.maxMemory() / (1024 * 1024)); // MB

        return GSON.toJson(stats);
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
        try {
            List<PluginBase> loadedPlugins = PluginManager.get().getPlugins();
            for (PluginBase plugin : loadedPlugins) {
                String name = plugin.getManifest().getName();
                if (INTERNAL_PLUGINS.contains(name)) continue;

                JsonObject pObj = new JsonObject();
                pObj.addProperty("name", name);
                pObj.addProperty("version", plugin.getManifest().getVersion().toString());
                pObj.addProperty("id", plugin.getIdentifier().toString());
                plugins.add(pObj);
            }
        } catch (Exception e) {
            LOGGER.warning("Error getting plugins: " + e.getMessage());
            // Fallback for safety
            JsonObject pObj = new JsonObject();
            pObj.addProperty("name", "Admin Dashboard");
            pObj.addProperty("version", "0.1");
            plugins.add(pObj);
        }
        return GSON.toJson(plugins);
    }

    private static String getChatLog() {
        return GSON.toJson(ChatLog.getMessages());
    }

    private static String kickPlayer(String body) {
        try {
            JsonObject json = GSON.fromJson(body, JsonObject.class);
            if (!json.has("uuid")) return "{\"error\": \"Missing UUID\"}";
            
            UUID uuid = UUID.fromString(json.get("uuid").getAsString());
            String reason = json.has("reason") ? json.get("reason").getAsString() : "Kicked by Admin";
            
            PlayerRef ref = Universe.get().getPlayer(uuid);
            if (ref != null && ref.getPacketHandler() != null) {
                ref.getPacketHandler().disconnect(reason);
                return "{\"status\": \"success\", \"message\": \"Player kicked\"}";
            }
            return "{\"error\": \"Player or connection not found\"}";
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
}
