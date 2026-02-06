# Admin Dashboard

A powerful web-based administration panel for Hytale servers, providing real-time monitoring and management capabilities.

## Features

### Player Management
- **Real-time player monitoring** with live stats (Health, Stamina, Mana, Defense)
- **Player actions**: Kick, Ban, Toggle OP, Teleport players
- **Inventory viewer** with visual item display
- **Player coordinates** and game mode tracking
- **Search and filter** players instantly

### Ban Management
- **Ban/Unban players** with custom reasons
- **View banned players list** with timestamps
- **Direct file access** to bans.json for verification
- Automatic ban list synchronization

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

## Installation

1. Download the latest `AdminDashboard-X.X.jar` from releases
2. Place the JAR file in your Hytale server's `Mods` folder
3. Start or restart your Hytale server
4. The dashboard will be accessible at `http://localhost:9081`

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
