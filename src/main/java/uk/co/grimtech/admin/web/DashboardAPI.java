package uk.co.grimtech.admin.web;

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
import com.hypixel.hytale.math.vector.Vector3d;

import java.util.List;
import java.util.UUID;

public class DashboardAPI {
    private static final Gson GSON = new Gson();

    public static String handleRequest(String path, String method, String body) {
        if (path.equals("/api/players")) {
            return getPlayers();
        } else if (path.startsWith("/api/player/") && path.endsWith("/inv")) {
            String uuidStr = path.substring(12, path.length() - 4);
            return getPlayerInventory(uuidStr);
        } else if (path.equals("/api/kick") && method.equals("POST")) {
            return kickPlayer(body);
        } else if (path.equals("/api/stats")) {
            return getServerStats();
        }
        
        return "{\"error\": \"Invalid endpoint\"}";
    }

    private static String getPlayers() {
        JsonArray playersArray = new JsonArray();
        List<PlayerRef> players = Universe.get().getPlayers();
        
        for (PlayerRef ref : players) {
            JsonObject playerJson = new JsonObject();
            playerJson.addProperty("name", ref.getUsername());
            playerJson.addProperty("uuid", ref.getUuid().toString());
            
            // Try to get dynamic data if player is in a world
            var entityRef = ref.getReference();
            if (entityRef != null && entityRef.isValid()) {
                var store = entityRef.getStore();
                Player playerComp = store.getComponent(entityRef, Player.getComponentType());
                if (playerComp != null) {
                    EntityStatMap statMap = store.getComponent(entityRef, EntityStatMap.getComponentType());
                    if (statMap != null) {
                        EntityStatValue healthStat = statMap.get(DefaultEntityStatTypes.getHealth());
                        if (healthStat != null) {
                            playerJson.addProperty("health", healthStat.get());
                            playerJson.addProperty("maxHealth", healthStat.getMax());
                        }
                    }
                    playerJson.addProperty("gameMode", playerComp.getGameMode().toString());
                    
                    TransformComponent transform = store.getComponent(entityRef, TransformComponent.getComponentType());
                    if (transform != null) {
                        Vector3d pos = transform.getPosition();
                        playerJson.addProperty("x", Math.round(pos.x * 100.0) / 100.0);
                        playerJson.addProperty("y", Math.round(pos.y * 100.0) / 100.0);
                        playerJson.addProperty("z", Math.round(pos.z * 100.0) / 100.0);
                    }
                }
            }
            playersArray.add(playerJson);
        }
        
        return GSON.toJson(playersArray);
    }

    private static String getPlayerInventory(String uuidStr) {
        try {
            UUID uuid = UUID.fromString(uuidStr);
            PlayerRef ref = Universe.get().getPlayer(uuid);
            if (ref == null) return "{\"error\": \"Player not found\"}";

            JsonObject invJson = new JsonObject();
            var entityRef = ref.getReference();
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
        stats.addProperty("uptime", System.currentTimeMillis()); // Placeholder
        return GSON.toJson(stats);
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
