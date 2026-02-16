# Admin WebDash

**Version 1.0.1**

A powerful web-based administration panel for Hytale servers, providing real-time monitoring and management capabilities through a beautiful Hytale-themed interface.

## Features

### Dashboard Overview
- **Live Server Stats**: Real-time TPS, Memory Usage, and Player Count monitoring.
- **Server Activity**: Monitor chat and console logs in real-time.
- **Quick Actions**: Common tasks accessible directly from the main dashboard.

### Player Management
- **Unified Player List**: View all online players with detailed stats (Health, Stamina, Mana, Defense).
- **Moderation Tools**: Built-in Ban, Kick, and Mute (temp/perm) functionality.
- **Inventory Viewer**: See player inventories in real-time.
- **Player Actions**: 
  - Heal / Feed
  - Clear Inventory
  - Change Gamemode (Adventure/Creative)
  - Teleport
  - Give Items (with visual item browser)

### World & Environment
- **Multi-World Support**: Monitor all loaded worlds and their individual performance (TPS).
- **Time & Weather**: Control time of day (Day, Night, Noon, Midnight) and weather (Clear, Rain, Storm).
- **Warp System**: Create, delete, and teleport to warp points.

### Advanced Metrics
- **System Performance**: Detailed breakdown of CPU Load, Heap Memory, and Non-Heap Memory.
- **Thread Analysis**: Monitor active thread counts and Garbage Collection stats.
- **World Performance**: Per-world TPS tracking to identify lag sources.

### File Management
- **Backup System**: 
  - Create full server backups with one click.
  - Schedule automatic backups (configurable interval).
  - Restore server from backups.
  - Download or delete old backups.
- **Log Explorer**: Browse, view, and download server log files directly from the browser.
- **Mod List**: View installed mods with metadata and CurseForge integration.

### Server Configuration
- **Web Config Editor**: Modify server settings (MOTD, Max Players, View Radius) without restarting (hot-reload supported).
- **Dynamic Logging**: Adjust log levels for specific packages on the fly for debugging.

---

## Installation

### Standard Installation

1. Download `AdminWebDash-1.0.1.jar` from the releases page.
2. Place the JAR file in your Hytale server's `mods` folder.
3. Start your Hytale server.
4. Check the console for the **Admin Token** and **Dashboard URL**.
5. Open your browser and navigate to `http://localhost:9081` (default).

### Docker Installation

If running in Docker, map the dashboard port in your `docker-compose.yml`:

```yaml
services:
  hytale:
    image: ghcr.io/machinastudios/hytale
    ports:
      - "9081:9081"  # Admin WebDash
    volumes:
      - ./mods:/hytale/mods
    # ... other config
```

---

## Configuration

The mod creates a `config.json` in `mods/AdminWebDash/`.

```json
{
  "port": 9081,
  "adminToken": "your-secure-token",
  "backupInterval": 0,
  "loggingEnabled": false
}
```

| Option | Description | Default |
|Wrapper|---|---|
| `port` | Web server port. Set to `0` for random. | `9081` |
| `adminToken` | Security token for login. Auto-generated. | `(Random)` |
| `backupInterval` | Auto-backup interval in minutes. `0` = Disabled. | `0` |
| `loggingEnabled` | Write dashboard logs to `logs/dashboard.log`. | `false` |

---

## Security

- **Token Authentication**: Access restricted by a secure token.
- **Local Access Only**: By default, the dashboard is accessible on localhost. Use a reverse proxy (Nginx/Apache) for external access with SSL.

---

## Keyboard Shortcuts

| Key | Action |
|---|---|
| `Ctrl + R` | Refresh Data |
| `Ctrl + F` | Focus Search |
| `Esc` | Close Modals |

---

## Support & Credits

Created by **Patrick Jr.**

- **Discord**: [Join the Community](https://discord.gg/YUxuTa4ZzX)
- **CurseForge**: [PatrickJr's Projects](https://www.curseforge.com/members/patrickjr/projects)

*All rights reserved. This mod is provided as-is for use with Hytale servers.*
