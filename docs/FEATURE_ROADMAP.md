# Admin Dashboard - Feature Roadmap

Based on analysis of AdminUI-1.0.5 mod capabilities, here are the features we can integrate into the web dashboard.

## 🎯 Priority 1 - High Impact Features

### 1. Mute System
**Why:** Essential moderation tool for managing disruptive players without banning them.

**Backend API Endpoints:**
- `POST /api/mute` - Mute a player with optional duration
- `POST /api/unmute` - Unmute a player
- `GET /api/mutes` - Get list of all muted players

**Frontend UI:**
- Add "Mute" button to player actions modal
- New "Muted Players" accordion section (similar to banned players)
- Mute duration selector (5min, 30min, 1hr, 1day, permanent)
- Display remaining mute time

**Implementation:**
```java
// MuteTracker utility class (similar to AdminUI)
public class MuteTracker {
    private Map<UUID, Mute> mutes = new HashMap<>();
    
    public static class Mute {
        UUID player;
        UUID mutedBy;
        Instant timestamp;
        Duration duration; // null = permanent
        String reason;
    }
}
```

---

### 2. Whitelist Management
**Why:** Critical for private servers and controlled access.

**Backend API Endpoints:**
- `GET /api/whitelist` - Get whitelist status and players
- `POST /api/whitelist/enable` - Enable whitelist mode
- `POST /api/whitelist/disable` - Disable whitelist mode
- `POST /api/whitelist/add` - Add player to whitelist
- `POST /api/whitelist/remove` - Remove player from whitelist

**Frontend UI:**
- New "Whitelist" tab/section
- Toggle switch for whitelist mode
- Search and add players by username/UUID
- List of whitelisted players with remove buttons
- Bulk import from file

**Implementation:**
```java
// Use Hytale's AccessControlModule
AccessControlModule.get().getWhitelistProvider()
```

---

### 3. Warp System
**Why:** Makes server navigation easier for admins and players.

**Backend API Endpoints:**
- `GET /api/warps` - Get all warp points
- `POST /api/warp/create` - Create new warp
- `POST /api/warp/delete` - Delete warp
- `POST /api/warp/teleport` - Teleport player to warp

**Frontend UI:**
- New "Warps" section in side panel
- Create warp button with name input
- List of warps with coordinates
- Click to teleport selected player to warp
- Edit/delete warp buttons

**Data Structure:**
```json
{
  "name": "spawn",
  "world": "world_1",
  "x": 100.5,
  "y": 64.0,
  "z": -50.3,
  "createdBy": "admin-uuid",
  "createdAt": 1234567890
}
```

---

## 🎯 Priority 2 - Enhanced Player Management

### 4. Gamemode Switching
**Backend:** `POST /api/gamemode` with `{uuid, mode}`
**Frontend:** Dropdown in player actions modal (Creative/Survival/Adventure)

### 5. Give Items
**Backend:** `POST /api/give` with `{uuid, itemId, quantity}`
**Frontend:** Item search/selector with quantity input

### 6. Heal Player
**Backend:** `POST /api/heal` with `{uuid}`
**Frontend:** "Heal" button in player actions (restores health/stamina/mana to max)

### 7. Clear Inventory
**Backend:** `POST /api/clearinv` with `{uuid}`
**Frontend:** "Clear Inventory" button with confirmation dialog

---

## 🎯 Priority 3 - World Management

### 8. Time Control
**Backend:** `POST /api/time` with `{time}` (0-24000 or preset: day/night/noon/midnight)
**Frontend:** Time slider or preset buttons in server controls

### 9. Weather Control
**Backend:** `POST /api/weather` with `{weather}` (clear/rain/storm)
**Frontend:** Weather buttons in server controls

### 10. World Info
**Backend:** `GET /api/world` - Returns world seed, spawn, size, etc.
**Frontend:** New "World Info" card showing world details

---

## 🎯 Priority 4 - Advanced Features

### 11. Server Backup Management
**Backend:**
- `POST /api/backup/create` - Trigger manual backup
- `GET /api/backup/list` - List all backups
- `POST /api/backup/restore` - Restore from backup

**Frontend:**
- New "Backups" section
- Backup now button
- List of backups with size, date, restore button
- Backup configuration (auto-backup interval)

### 12. Permission Management
**Backend:**
- `GET /api/permissions/{uuid}` - Get player permissions
- `POST /api/permissions/grant` - Grant permission
- `POST /api/permissions/revoke` - Revoke permission
- `GET /api/groups` - List permission groups

**Frontend:**
- New "Permissions" tab
- Permission tree viewer
- Grant/revoke permission buttons
- Group management interface

---

## 📊 Implementation Priority Order

1. **Phase 1 (Quick Wins):**
   - Mute System
   - Gamemode Switching
   - Heal Player
   - Time/Weather Control

2. **Phase 2 (Moderate Complexity):**
   - Whitelist Management
   - Warp System
   - Give Items
   - Clear Inventory

3. **Phase 3 (Complex Features):**
   - Server Backup Management
   - Permission Management
   - World Info Dashboard

---

## 🛠️ Technical Implementation Notes

### Backend (Java)
- Add new endpoints to `DashboardAPI.java`
- Create utility classes for new features (MuteTracker, WarpManager, etc.)
- Use Hytale's existing modules where possible (AccessControlModule, PermissionsModule)
- Ensure thread-safe operations on World threads

### Frontend (JavaScript)
- Add new UI sections to `index.html`
- Create new API call functions in `dashboard.js`
- Maintain consistent Hytale-themed styling
- Add loading states and error handling

### Data Persistence
- Store warps in `warps.json`
- Store mutes in `mutes.json`
- Use Hytale's built-in whitelist system
- Backup configs in `backups/` directory

---

## 🎨 UI/UX Improvements

### New Sections to Add:
1. **Server Controls Panel** (Time, Weather, Backup)
2. **Moderation Panel** (Mutes, Whitelist)
3. **Warps Panel** (Create, List, Teleport)
4. **Permissions Panel** (View, Grant, Revoke)

### Enhanced Player Actions Modal:
```
Current: [Inventory] [Teleport] [OP] [Ban] [Kick]
New:     [Inventory] [Teleport] [OP] [Gamemode] [Heal] [Give Item] 
         [Clear Inv] [Mute] [Ban] [Kick]
```

---

## 📝 Next Steps

1. Choose which features to implement first
2. Create backend API endpoints
3. Test with Hytale server
4. Build frontend UI components
5. Add error handling and validation
6. Update documentation
7. Test thoroughly
8. Deploy and gather feedback

---

## 💡 Additional Ideas

- **Player Notes:** Add notes/tags to players (VIP, Trusted, Watch, etc.)
- **Command History:** Log all admin actions for audit trail
- **Scheduled Tasks:** Schedule commands to run at specific times
- **Player Statistics:** Track playtime, deaths, blocks broken, etc.
- **Economy Integration:** If economy mod installed, view/edit player balances
- **Chat Filters:** Configure automatic chat moderation rules
- **Server Announcements:** Schedule recurring server messages
- **Player Warnings:** Issue warnings before bans (3-strike system)

