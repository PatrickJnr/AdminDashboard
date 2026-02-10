# Admin Dashboard

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

### Modern UI
- **Hytale-themed design** matching the official website styling
- **Responsive layout** works on desktop and mobile
- **Smooth, polished animations** and transitions
- **Keyboard shortcuts** for quick actions (Ctrl+R to refresh, Ctrl+F to search)
- **Live connection status** with automatic reconnection
- **Material Symbols icons** throughout
- **Expandable sections** for organized content
- **Visual item browser** with search and icons
- **Custom modals** for all interactions

## Installation

### Standard Installation

1. Download `AdminWebDash-1.0.jar` from the releases page
2. Place the JAR file in your Hytale server's `Mods` folder
3. Start or restart your Hytale server
4. Check the console for the dashboard URL and admin token
5. The dashboard will be accessible at the displayed URL (default: `http://localhost:9081`)

### Docker Installation

If you're running your Hytale server in Docker, follow these additional steps:

1. **Mount the Mods folder** as a volume in your Docker container:
   ```bash
   docker run -v /path/to/mods:/server/Mods your-hytale-image
   ```

2. **Expose the dashboard port** (default 9081, or your configured port) in your Docker configuration:
   ```bash
   docker run -p 9081:9081 -v /path/to/mods:/server/Mods your-hytale-image
   ```

3. **Using Docker Compose**, add this to your `docker-compose.yml`:
   ```yaml
   services:
     hytale-server:
       image: your-hytale-image
       ports:
         - "9081:9081"  # Admin Dashboard (change if you configured a different port)
         - "5520:5520"  # Game server port
       volumes:
         - ./mods:/server/Mods
         - ./config:/server/config
   ```

4. **Access the dashboard** from your host machine at `http://localhost:9081` (or your configured port)

**Docker Network Notes:**
- If accessing from outside the Docker host, use the host's IP address instead of `localhost`
- For production deployments, consider using a reverse proxy (nginx, Traefik) for HTTPS
- Ensure firewall rules allow traffic on your configured port if accessing remotely

## Configuration

The mod creates a configuration file at `mods/AdminDashboard/config.json`:

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

1. After starting the server, check the console output or `mods/AdminDashboard/config.json` for your admin token and dashboard URL
2. Open your browser and navigate to the URL shown (default: `http://localhost:9081`)
3. Enter the admin token from the config file
4. You're ready to manage your server!

**Tip:** Bookmark the dashboard URL for quick access!

## Security

- **Token-based authentication** protects all API endpoints
- Change the default admin token in the config file
- Keep your admin token secure and don't share it publicly
- The dashboard is only accessible from the server's network by default

## Logging

The mod uses a custom logging system that writes to `logs/dashboard.log` instead of cluttering the main server log. Logging is disabled by default but can be enabled by setting `loggingEnabled: true` in the config.

## Keyboard Shortcuts

- **Ctrl/Cmd + R**: Refresh dashboard data
- **Ctrl/Cmd + F**: Focus search box
- **Escape**: Close open modals

## Requirements

- Hytale Server (compatible version)
- Java 25 or higher
- Modern web browser (Chrome, Firefox, Edge, Safari)

## Support

For issues, feature requests, or questions:
- Visit my CurseForge profile: https://www.curseforge.com/members/patrickjr/projects
- Report bugs on the mod page

## Credits

Created by **Patrick Jr.**

Special thanks to the Hytale modding community for their support and feedback.

## License

All rights reserved. This mod is provided as-is for use with Hytale servers.
