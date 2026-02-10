# Changelog

## [Unreleased] - 2026-02-09

### 🎉 Major Feature Update - AdminUI Integration

This update brings **8 major new features** inspired by the AdminUI mod, significantly expanding the dashboard's capabilities.

### ✨ Added

#### Player Management
- **Gamemode Switching** - Quickly change players between Creative, Survival, and Adventure modes
- **Heal Player** - Instantly restore health, stamina, and mana to maximum values
- **Give Items** - Give any item to players with custom quantities (1-999)
- **Clear Inventory** - Remove all items from a player's inventory with confirmation

#### Moderation Tools
- **Mute System** - Mute players temporarily or permanently
  - Duration options: 5 minutes, 30 minutes, 1 hour, 1 day, or permanent
  - Automatic expiration for temporary mutes
  - Remaining time display
  - Reason tracking
  - Muted Players section with unmute functionality

#### World Management
- **Time Control** - Set server time instantly
  - Presets: Day, Night, Noon, Midnight
  - Affects all worlds simultaneously
- **Weather Control** - Change weather conditions
  - Options: Clear, Rain, Storm
  - Affects all worlds simultaneously

#### Teleportation System
- **Warp Points** - Create and manage named teleport locations
  - Create warps at player locations
  - Teleport players to saved warps
  - Delete warps
  - View warp coordinates and world info
  - Persistent storage in warps.json

### 🎨 UI Improvements

#### Enhanced Player Actions Modal
- Expanded from 5 to 10 actions
- New action buttons:
  - Change Gamemode
  - Heal Player
  - Give Item
  - Clear Inventory
  - Mute Player
- Better organized with color-coded icons
- Improved layout and spacing

#### New Side Panel Sections
- **World Controls** - Time and weather management with preset buttons
- **Muted Players** - Accordion section showing all muted players with remaining time
- **Warp Points** - Accordion section for warp management with create/delete/teleport

#### Better UX
- Duration selector for mutes with user-friendly prompts
- Confirmation dialogs for destructive actions (ban, clear inventory)
- Real-time remaining time display for temporary mutes
- Coordinate display for warp points
- Auto-refresh for mutes and warps lists

### 🔧 Technical Improvements

#### New Backend Components
- `MuteTracker.java` - Manages mute records with automatic expiration
- `WarpManager.java` - Manages warp points with coordinate tracking
- 13 new API endpoints for all new features
- Thread-safe operations on World threads
- Comprehensive error handling and logging

#### Data Persistence
- `mutes.json` - Stores mute records
- `warps.json` - Stores warp points
- Auto-loading on server startup
- Automatic cleanup of expired mutes

#### API Endpoints
- `POST /api/gamemode` - Change player gamemode
- `POST /api/heal` - Heal player
- `POST /api/time` - Set world time
- `POST /api/weather` - Set weather
- `POST /api/mute` - Mute player
- `POST /api/unmute` - Unmute player
- `GET /api/mutes` - Get all mutes
- `GET /api/warps` - Get all warps
- `POST /api/warp/create` - Create warp
- `POST /api/warp/delete` - Delete warp
- `POST /api/warp/teleport` - Teleport to warp
- `POST /api/give` - Give item to player
- `POST /api/clearinv` - Clear player inventory

### 📚 Documentation
- Added `FEATURE_ROADMAP.md` - Complete feature analysis and future plans
- Added `QUICK_START_GUIDE.md` - Step-by-step implementation guide
- Added `IMPLEMENTATION_SUMMARY.md` - Detailed technical documentation
- Updated `README.md` - Comprehensive feature list

### 🔄 Changed
- Updated player actions modal layout for better organization
- Improved side panel with new expandable sections
- Enhanced startSync() to fetch mutes and warps data
- Updated README with all new features

### 📊 Statistics
- **Lines of Code Added**: ~1,200+
- **New Features**: 8
- **New API Endpoints**: 13
- **New Utility Classes**: 2
- **New UI Sections**: 3
- **New Action Buttons**: 5

---

## [0.1.0] - Previous Release

### Initial Features
- Real-time player monitoring
- Player inventory viewer
- Ban/Kick/OP management
- Server statistics (TPS, memory, uptime)
- Broadcast messages
- Chat log monitoring
- Mod list with CurseForge integration
- Hytale-themed UI
- Token-based authentication

