# Admin Dashboard

A powerful web-based administration panel for Hytale servers, providing real-time monitoring and management capabilities.

## Features

### Player Management
- **Real-time player monitoring** with live stats (Health, Stamina, Mana, Defense)
- **Player actions**: Kick, Ban, Toggle OP, Teleport players
- **Gamemode switching**: Quickly change between Creative, Survival, and Adventure modes
- **Heal players**: Instantly restore health, stamina, and mana to maximum
- **Give items**: Give any item to players with custom quantities
- **Clear inventory**: Remove all items from a player's inventory
- **Inventory viewer** with visual item display
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
- **Smooth animations** and transitions
- **Keyboard shortcuts** for quick actions (Ctrl+R to refresh, Ctrl+F to search)
- **Connection status indicator** with automatic reconnection
- **Material Symbols icons** throughout
- **Expandable sections** for organized content

## Installation

### Standard Installation

1. Download the latest `AdminDashboard-X.X.jar` from releases
2. Place the JAR file in your Hytale server's `Mods` folder
3. Start or restart your Hytale server
4. The dashboard will be accessible at `http://localhost:9081`

### Docker Installation

If you're running your Hytale server in Docker, follow these additional steps:

1. **Mount the Mods folder** as a volume in your Docker container:
   ```bash
   docker run -v /path/to/mods:/server/Mods your-hytale-image
   ```

2. **Expose the dashboard port** (default 9081) in your Docker configuration:
   ```bash
   docker run -p 9081:9081 -v /path/to/mods:/server/Mods your-hytale-image
   ```

3. **Using Docker Compose**, add this to your `docker-compose.yml`:
   ```yaml
   services:
     hytale-server:
       image: your-hytale-image
       ports:
         - "9081:9081"  # Admin Dashboard
         - "5520:5520"  # Game server port
       volumes:
         - ./mods:/server/Mods
         - ./config:/server/config
   ```

4. **Access the dashboard** from your host machine at `http://localhost:9081`

**Docker Network Notes:**
- If accessing from outside the Docker host, use the host's IP address instead of `localhost`
- For production deployments, consider using a reverse proxy (nginx, Traefik) for HTTPS
- Ensure firewall rules allow traffic on port 9081 if accessing remotely

## Configuration

The mod creates a configuration file at `mods/AdminDashboard/config.json`:

```json
{
  "port": 9081,
  "adminToken": "your-secure-token-here",
  "loggingEnabled": true
}
```

### Configuration Options

- **port**: The port number for the web dashboard (default: 9081)
- **adminToken**: Security token required to access the dashboard (change this!)
- **loggingEnabled**: Enable/disable custom logging to `logs/dashboard.log` (default: true)

## First Time Setup

1. After starting the server, check `mods/AdminDashboard/config.json` for your admin token
2. Open your browser and navigate to `http://localhost:9081`
3. Enter the admin token from the config file
4. You're ready to manage your server!

## Security

- **Token-based authentication** protects all API endpoints
- Change the default admin token in the config file
- Keep your admin token secure and don't share it publicly
- The dashboard is only accessible from the server's network by default

## Logging

The mod uses a custom logging system that writes to `logs/dashboard.log` instead of cluttering the main server log. You can disable logging by setting `loggingEnabled: false` in the config.

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
