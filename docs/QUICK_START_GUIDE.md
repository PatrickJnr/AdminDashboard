# Quick Start Guide - Adding New Features

This guide shows you how to quickly add the most impactful features from AdminUI to your web dashboard.

## 🚀 Quick Win #1: Gamemode Switching

### Backend (5 minutes)

Add to `DashboardAPI.java`:

```java
private static String setGamemode(String body) {
    try {
        JsonObject json = GSON.fromJson(body, JsonObject.class);
        if (!json.has("uuid") || !json.has("gamemode")) {
            return "{\"error\": \"Missing UUID or gamemode\"}";
        }
        
        UUID uuid = UUID.fromString(json.get("uuid").getAsString());
        String gamemodeStr = json.get("gamemode").getAsString().toUpperCase();
        
        PlayerRef ref = Universe.get().getPlayer(uuid);
        if (ref == null) return "{\"error\": \"Player not found\"}";
        
        Ref<EntityStore> entityRef = ref.getReference();
        if (entityRef == null || !entityRef.isValid()) {
            return "{\"error\": \"Player not in world\"}";
        }
        
        Store<EntityStore> store = entityRef.getStore();
        World world = store.getExternalData().getWorld();
        
        CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
            try {
                Player player = store.getComponent(entityRef, Player.getComponentType());
                if (player != null) {
                    GameMode mode = GameMode.valueOf(gamemodeStr);
                    player.setGameMode(mode);
                    return true;
                }
                return false;
            } catch (Exception e) {
                getLogger().log("ERROR", "Error setting gamemode", e);
                return false;
            }
        }, world);
        
        boolean success = future.get(2, TimeUnit.SECONDS);
        if (success) {
            getLogger().info("[API] Set " + ref.getUsername() + " to " + gamemodeStr);
            return "{\"status\": \"success\"}";
        }
        return "{\"error\": \"Failed to set gamemode\"}";
    } catch (Exception e) {
        return "{\"error\": \"" + e.getMessage() + "\"}";
    }
}
```

Add to `handleRequest()`:
```java
} else if (path.equals("/api/gamemode") && method.equals("POST")) {
    return setGamemode(body);
```

### Frontend (5 minutes)

Add to `dashboard.js`:

```javascript
async function setGamemode(uuid, gamemode) {
    try {
        const response = await fetch('/api/gamemode', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${localStorage.getItem('adminToken')}`
            },
            body: JSON.stringify({ uuid, gamemode })
        });
        const data = await response.json();
        if (data.status === 'success') {
            showNotification('Gamemode changed successfully', 'success');
            fetchPlayers();
        } else {
            showNotification(data.error || 'Failed to change gamemode', 'error');
        }
    } catch (error) {
        showNotification('Error changing gamemode', 'error');
    }
}
```

Add to actions modal in `index.html`:

```html
<button class="action-card" id="action-gamemode">
    <span class="material-symbols-outlined action-card-icon" style="color: #9b59b6;">sports_esports</span>
    <div class="action-card-title">Change Gamemode</div>
    <div class="action-card-desc">Switch between Creative/Survival</div>
</button>
```

Add click handler:
```javascript
document.getElementById('action-gamemode').onclick = () => {
    const modes = ['CREATIVE', 'SURVIVAL', 'ADVENTURE'];
    const mode = prompt('Enter gamemode (CREATIVE, SURVIVAL, ADVENTURE):');
    if (mode && modes.includes(mode.toUpperCase())) {
        setGamemode(currentPlayerUuid, mode.toUpperCase());
        closeActionsModal();
    }
};
```

---

## 🚀 Quick Win #2: Heal Player

### Backend (3 minutes)

```java
private static String healPlayer(String body) {
    try {
        JsonObject json = GSON.fromJson(body, JsonObject.class);
        if (!json.has("uuid")) return "{\"error\": \"Missing UUID\"}";
        
        UUID uuid = UUID.fromString(json.get("uuid").getAsString());
        PlayerRef ref = Universe.get().getPlayer(uuid);
        if (ref == null) return "{\"error\": \"Player not found\"}";
        
        Ref<EntityStore> entityRef = ref.getReference();
        if (entityRef == null || !entityRef.isValid()) {
            return "{\"error\": \"Player not in world\"}";
        }
        
        Store<EntityStore> store = entityRef.getStore();
        World world = store.getExternalData().getWorld();
        
        CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
            try {
                EntityStatMap statMap = store.getComponent(entityRef, EntityStatMap.getComponentType());
                if (statMap != null) {
                    // Heal health
                    EntityStatValue health = statMap.get(DefaultEntityStatTypes.getHealth());
                    if (health != null) health.set(health.getMax());
                    
                    // Restore stamina
                    EntityStatValue stamina = statMap.get(DefaultEntityStatTypes.getStamina());
                    if (stamina != null) stamina.set(stamina.getMax());
                    
                    // Restore mana
                    EntityStatValue mana = statMap.get(DefaultEntityStatTypes.getMana());
                    if (mana != null) mana.set(mana.getMax());
                    
                    return true;
                }
                return false;
            } catch (Exception e) {
                getLogger().log("ERROR", "Error healing player", e);
                return false;
            }
        }, world);
        
        boolean success = future.get(2, TimeUnit.SECONDS);
        if (success) {
            getLogger().info("[API] Healed " + ref.getUsername());
            return "{\"status\": \"success\"}";
        }
        return "{\"error\": \"Failed to heal player\"}";
    } catch (Exception e) {
        return "{\"error\": \"" + e.getMessage() + "\"}";
    }
}
```

Add endpoint:
```java
} else if (path.equals("/api/heal") && method.equals("POST")) {
    return healPlayer(body);
```

### Frontend (2 minutes)

```javascript
async function healPlayer(uuid) {
    try {
        const response = await fetch('/api/heal', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${localStorage.getItem('adminToken')}`
            },
            body: JSON.stringify({ uuid })
        });
        const data = await response.json();
        if (data.status === 'success') {
            showNotification('Player healed successfully', 'success');
            fetchPlayers();
        }
    } catch (error) {
        showNotification('Error healing player', 'error');
    }
}
```

Add button to actions modal:
```html
<button class="action-card" id="action-heal">
    <span class="material-symbols-outlined action-card-icon" style="color: #e74c3c;">favorite</span>
    <div class="action-card-title">Heal Player</div>
    <div class="action-card-desc">Restore health, stamina, and mana</div>
</button>
```

---

## 🚀 Quick Win #3: Time Control

### Backend (4 minutes)

```java
private static String setTime(String body) {
    try {
        JsonObject json = GSON.fromJson(body, JsonObject.class);
        if (!json.has("time")) return "{\"error\": \"Missing time\"}";
        
        String timeStr = json.get("time").getAsString();
        long ticks;
        
        // Support presets
        switch (timeStr.toLowerCase()) {
            case "day":
            case "morning":
                ticks = 1000;
                break;
            case "noon":
                ticks = 6000;
                break;
            case "night":
            case "evening":
                ticks = 13000;
                break;
            case "midnight":
                ticks = 18000;
                break;
            default:
                ticks = Long.parseLong(timeStr);
        }
        
        // Set time in all worlds
        for (World world : Universe.get().getWorlds().values()) {
            world.execute(() -> {
                WorldTimeResource timeResource = world.getResource(WorldTimeResource.class);
                if (timeResource != null) {
                    timeResource.setTime(ticks);
                }
            });
        }
        
        getLogger().info("[API] Set time to " + ticks);
        return "{\"status\": \"success\", \"time\": " + ticks + "}";
    } catch (Exception e) {
        return "{\"error\": \"" + e.getMessage() + "\"}";
    }
}
```

### Frontend (3 minutes)

Add new section to `index.html`:

```html
<div class="hytale-panel" style="margin-top: 2rem">
    <div class="hytale-panel-inner">
        <div class="card-header">
            <h2>World Controls</h2>
        </div>
        <div style="display: grid; grid-template-columns: repeat(2, 1fr); gap: 0.5rem;">
            <button class="btn btn-secondary" onclick="setTime('day')">
                <span class="material-symbols-outlined">wb_sunny</span>
                Day
            </button>
            <button class="btn btn-secondary" onclick="setTime('night')">
                <span class="material-symbols-outlined">nightlight</span>
                Night
            </button>
            <button class="btn btn-secondary" onclick="setTime('noon')">
                <span class="material-symbols-outlined">light_mode</span>
                Noon
            </button>
            <button class="btn btn-secondary" onclick="setTime('midnight')">
                <span class="material-symbols-outlined">dark_mode</span>
                Midnight
            </button>
        </div>
    </div>
</div>
```

JavaScript:
```javascript
async function setTime(time) {
    try {
        const response = await fetch('/api/time', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${localStorage.getItem('adminToken')}`
            },
            body: JSON.stringify({ time })
        });
        const data = await response.json();
        if (data.status === 'success') {
            showNotification(`Time set to ${time}`, 'success');
        }
    } catch (error) {
        showNotification('Error setting time', 'error');
    }
}
```

---

## 🚀 Quick Win #4: Mute System

### Backend (10 minutes)

Create `MuteTracker.java`:

```java
package uk.co.grimtech.admin.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MuteTracker {
    private static final Gson GSON = new Gson();
    private static final Path MUTES_FILE = Paths.get("mutes.json");
    private static final Map<UUID, Mute> mutes = new ConcurrentHashMap<>();
    
    public static class Mute {
        public UUID player;
        public UUID mutedBy;
        public Instant timestamp;
        public Long durationSeconds; // null = permanent
        public String reason;
        
        public boolean isExpired() {
            if (durationSeconds == null) return false;
            return Instant.now().isAfter(timestamp.plusSeconds(durationSeconds));
        }
    }
    
    public static void load() {
        if (Files.exists(MUTES_FILE)) {
            try {
                String json = Files.readString(MUTES_FILE);
                Map<UUID, Mute> loaded = GSON.fromJson(json, 
                    new TypeToken<Map<UUID, Mute>>(){}.getType());
                if (loaded != null) {
                    mutes.putAll(loaded);
                    // Remove expired mutes
                    mutes.entrySet().removeIf(e -> e.getValue().isExpired());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    public static void save() {
        try {
            Files.writeString(MUTES_FILE, GSON.toJson(mutes));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static void mute(UUID player, UUID mutedBy, Long durationSeconds, String reason) {
        Mute mute = new Mute();
        mute.player = player;
        mute.mutedBy = mutedBy;
        mute.timestamp = Instant.now();
        mute.durationSeconds = durationSeconds;
        mute.reason = reason;
        mutes.put(player, mute);
        save();
    }
    
    public static void unmute(UUID player) {
        mutes.remove(player);
        save();
    }
    
    public static boolean isMuted(UUID player) {
        Mute mute = mutes.get(player);
        if (mute == null) return false;
        if (mute.isExpired()) {
            unmute(player);
            return false;
        }
        return true;
    }
    
    public static Map<UUID, Mute> getMutes() {
        // Remove expired
        mutes.entrySet().removeIf(e -> e.getValue().isExpired());
        return new HashMap<>(mutes);
    }
}
```

Add to `DashboardAPI.java`:

```java
private static String mutePlayer(String body) {
    try {
        JsonObject json = GSON.fromJson(body, JsonObject.class);
        UUID uuid = UUID.fromString(json.get("uuid").getAsString());
        String reason = json.has("reason") ? json.get("reason").getAsString() : "Muted by admin";
        Long duration = json.has("duration") ? json.get("duration").getAsLong() : null;
        
        UUID adminUuid = UUID.fromString("00000000-0000-0000-0000-000000000000");
        MuteTracker.mute(uuid, adminUuid, duration, reason);
        
        return "{\"status\": \"success\"}";
    } catch (Exception e) {
        return "{\"error\": \"" + e.getMessage() + "\"}";
    }
}

private static String unmutePlayer(String body) {
    try {
        JsonObject json = GSON.fromJson(body, JsonObject.class);
        UUID uuid = UUID.fromString(json.get("uuid").getAsString());
        MuteTracker.unmute(uuid);
        return "{\"status\": \"success\"}";
    } catch (Exception e) {
        return "{\"error\": \"" + e.getMessage() + "\"}";
    }
}

private static String getMutes() {
    JsonArray mutesArray = new JsonArray();
    for (Map.Entry<UUID, MuteTracker.Mute> entry : MuteTracker.getMutes().entrySet()) {
        JsonObject muteJson = new JsonObject();
        muteJson.addProperty("uuid", entry.getKey().toString());
        muteJson.addProperty("mutedBy", entry.getValue().mutedBy.toString());
        muteJson.addProperty("timestamp", entry.getValue().timestamp.toEpochMilli());
        if (entry.getValue().durationSeconds != null) {
            muteJson.addProperty("duration", entry.getValue().durationSeconds);
        }
        muteJson.addProperty("reason", entry.getValue().reason);
        mutesArray.add(muteJson);
    }
    return GSON.toJson(mutesArray);
}
```

Initialize in plugin:
```java
MuteTracker.load();
```

---

## 📋 Testing Checklist

- [ ] Gamemode switching works for all modes
- [ ] Heal restores all stats to maximum
- [ ] Time control affects all worlds
- [ ] Mute persists across server restarts
- [ ] Mute expires automatically
- [ ] UI updates reflect changes immediately
- [ ] Error messages display correctly
- [ ] All features work with multiple players

---

## 🎨 UI Enhancement Tips

1. **Add loading states** - Show spinners during API calls
2. **Add confirmation dialogs** - For destructive actions (ban, kick, clear inventory)
3. **Add success notifications** - Visual feedback for completed actions
4. **Add keyboard shortcuts** - Quick access to common actions
5. **Add tooltips** - Explain what each button does

---

## 📚 Next Steps

After implementing these quick wins:

1. Test thoroughly with real players
2. Gather feedback on usability
3. Move to Priority 2 features (Warps, Whitelist)
4. Consider adding player notes/tags
5. Implement command history/audit log

