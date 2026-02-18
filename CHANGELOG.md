# Changelog

## Version 1.0.1 - February 18, 2026

### Maintenance
- Built for 2026.02.17-255364b8e

### Dashboard Update
- **Reorganized Sidebar:** Grouped navigation into logical categories for faster access
- **Unified Players Tab:** Merged Online Players and Moderation into a single view
- **Enhanced Metrics:** Moved system specifications to the Metrics tab
- **Files Tab:** Renamed "Server" to "Files" for clarity
- **Inventory rarity:** Added rarity colors to inventory slots to match in-game quality
- **Item movement:** Added drag-and-drop support to move items between inventory slots

### Improvements
- **Clean URLs:** Added direct navigation support for all dashboard tabs (prevents 404 on refresh)
- **System Stats:** Added Memory and Disk usage visualization to the Info tab
- **Log List Cleanup:** Hidden temporary `.lck` files

### New Features

**Advanced Metrics**
- New "Metrics" tab with live charts for CPU and Memory usage
- Real-time world performance monitoring (TPS) for all loaded worlds

**Log Explorer**
- New "Logs" tab to browse all server log files
- Built-in viewer to read logs directly in the dashboard
- One-click download for any log file

**World Management**
- New "World" tab to see all worlds on the server
- Monitor player counts and load/unload worlds dynamically

**Server Configuration**
- New "Config" tab for quick editing of server name, MOTD, and player limits

**Backup System**
- Create full server backups manually with a single click
- Schedule automatic backups at custom intervals
- View a detailed list of all backups with timestamps and file sizes
- Restore your server to any previous backup point (requires server restart)
- Delete old or unwanted backups to manage disk space
- Added confirmation dialogs for critical actions to prevent accidents

**Server Activity & Console**
- Renamed "Server Chat" to "Server Activity" to better reflect its purpose.
- Added **Server Console** view to monitor raw server logs in real-time.
- Toggle between formatted Chat and raw Console views instantly.

## Version 1.0.0 - February 10, 2026

### What's in the Mod

**Player Management**
- View all online players with real-time health, stamina, mana, and defense stats
- Change player gamemodes (Creative/Adventure)
- Heal players to full health
- Give items to players with a visual item browser
- Clear player inventories
- View player inventories with all their items
- Teleport players to each other
- Search and filter players

**Moderation Tools**
- Ban and unban players with custom reasons
- Kick players from the server
- Mute players (5min, 30min, 1hr, 1day, or permanent)
- View all banned and muted players
- Grant or revoke operator status
- Direct access to bans.json file

**World Controls**
- Set time of day (Sunrise, Sunset, Noon, Midnight)
- Change weather (Clear, Rain, Storm)
- Create and manage warp points
- Teleport players to saved locations with in-game chat feedback

**Server Features**
- Live server statistics (TPS, memory, player count)
- Real-time chat monitoring
- Broadcast messages to all players
- View installed mods with CurseForge integration
- Automatic mod detection and metadata
- Configurable port (default 9081, or set to 0 for random port)

**Web Interface**
- Beautiful Hytale-themed design
- Responsive layout for desktop and mobile
- Live connection status
- Keyboard shortcuts (Ctrl+R to refresh, Ctrl+F to search)
- Secure token-based authentication

Access your dashboard at `http://localhost:9081` (or your configured port) after installing the mod.

