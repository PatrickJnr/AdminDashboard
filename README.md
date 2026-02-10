# Admin WebDash

**Version 1.0.0**

A powerful web-based administration panel for Hytale servers, providing real-time monitoring and management capabilities through a beautiful Hytale-themed interface.

## Features

### Player Management
- **Real-time player monitoring** with live stats (Health, Stamina, Mana, Defense)
- **Player actions**: Kick, Ban, Toggle OP, Teleport players
- **Gamemode switching**: Visual button-based selection for Creative and Adventure modes
- **Heal players**: Instantly restore health, stamina, and mana to maximum
- **Give items**: Visual item browser with search, icons, and quantity selector
- **Clear inventory**: Remove all items from a player's inventory
- **Inventory viewer** with visual item display and tooltips
- **Player coordinates** and game mode tracking
- **Search and filter** players instantly

### Moderation Tools
- **Ban/Unban players** with custom reasons
- **Mute system** with duration options (5min, 30min, 1hr, 1day, permanent)
- **View muted players** with remaining time display
- **Kick players** temporarily from the server
- **View banned players list** with timestamps
- **Direct file access** to bans.json for verification
- Automatic ban and mute list synchronization

### World Management
- **Time control**: Set time to day, night, noon, or midnight instantly
- **Weather control**: Change weather to clear, rain, or storm
- **Warp system**: Create named teleport points and teleport players to them
- **Warp management**: View, create, and delete warp points with coordinates

### Server Communication
- **Broadcast messages** to all players
- **Live server chat** monitoring
- Real-time message updates

### Server Statistics
- **Live TPS monitoring** with color-coded performance indicators
- **Memory usage** tracking with automatic SI unit conversion
- **Online player count**
- **Server uptime** display

### Installed Mods
- **Automatic mod detection** from installed plugins
- **CurseForge integration** for rich mod metadata
- Display mod icons, authors, and download counts
- Direct links to CurseForge pages
- Alphabetically sorted mod list

### Web Interface
- Hytale-themed design matching the official website
- Responsive layout for desktop and mobile
- Live connection status with automatic reconnection
- Keyboard shortcuts (Ctrl+R to refresh, Ctrl+F to search)
- Visual item browser with search functionality

## Installation

### Standard Installation

1. Download `AdminWebDash-1.0.jar` from the releases page
2. Place the JAR file in your Hytale server's `Mods` folder
3. Start or restart your Hytale server
4. Check the console for the dashboard URL and admin token
5. The dashboard will be accessible at the displayed URL (default: `http://localhost:9081`)

### Docker Installation

If you're running your Hytale server in Docker:

1. **Place the mod** in your mounted mods directory
2. **Expose port 9081** in your docker-compose.yml:

```yaml
services:
  hytale:
    image: ghcr.io/machinastudios/hytale
    stdin_open: true
    tty: true
    ports:
      - "5520:5520/udp"
      - "9081:9081"  # Admin WebDash
    volumes:
      - ./mods:/hytale/mods
      - ./logs:/hytale/logs
      - ./universe:/hytale/universe
    environment:
      - SERVER_ACCEPT_EARLY_PLUGINS=true
      - SERVER_BIND=0.0.0.0:5520
```

3. **Restart your container** and access the dashboard at `http://localhost:9081`

**Note:** If you set `port: 0` in the config for a random port, Docker cannot dynamically map random ports. You must use a fixed port number (like 9081) in both the config and docker-compose.yml.

## Configuration

The mod creates a configuration file at `mods/AdminWebDash/config.json`:

```json
{
  "port": 9081,
  "adminToken": "your-secure-token-here",
  "loggingEnabled": false
}
```

### Configuration Options

- **port**: The port number for the web dashboard
  - Default: `9081`
  - Valid range: `1024-65535`
  - Set to `0` for a random available port (useful for avoiding conflicts)
- **adminToken**: Security token required to access the dashboard (auto-generated on first run)
- **loggingEnabled**: Enable/disable custom logging to `logs/dashboard.log` (default: false)

**Note:** After changing the port, restart your server for the changes to take effect. If using port `0`, check the console output for the actual assigned port.

## First Time Setup

1. After starting the server, check the console output or `mods/AdminWebDash/config.json` for your admin token and dashboard URL
2. Open your browser and navigate to the URL shown (default: `http://localhost:9081`)
3. Enter the admin token from the config file
4. You're ready to manage your server!

**Tip:** Bookmark the dashboard URL for quick access.

## Security

- Token-based authentication protects all API endpoints
- Change the auto-generated admin token in the config file
- Keep your admin token secure
- Dashboard is only accessible from the server's network by default

## Logging

Custom logging writes to `logs/dashboard.log`. Disabled by default, enable with `loggingEnabled: true` in config.

## Keyboard Shortcuts

- **Ctrl/Cmd + R**: Refresh dashboard data
- **Ctrl/Cmd + F**: Focus search box
- **Escape**: Close modals

## Requirements

- Hytale Server (compatible version)
- Java 25 or higher
- Modern web browser (Chrome, Firefox, Edge, Safari)

## Support

Need help or have questions?

**Discord Community**: https://discord.gg/CTVf2Ted - Get help, report bugs, and discuss features

## Credits

Created by **Patrick Jr.**

CurseForge: https://www.curseforge.com/members/patrickjr/projects

## License

All rights reserved. This mod is provided as-is for use with Hytale servers.
