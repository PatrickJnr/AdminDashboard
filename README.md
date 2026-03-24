# Admin WebDash
 
 **Version 1.0.1**
 
 A powerful, high-performance web administration panel for Hytale servers. Real-time monitoring, player management, and world control through a premium, Hytale-inspired interface.
 
 ## Key Features
 
 ### Dashboard & Monitoring
 - **Live Stats**: Real-time TPS, Memory Usage (Heap/Non-Heap), and Player Count.
 - **System Metrics**: Monitor CPU Load and Garbage Collection in real-time.
 - **Logs & Activity**: Live console and chat stream.
 
 ### Player Management
 - **Unified List**: Detailed stats (Health, Stamina, Mana, Defense) for all online players.
 - **Moderation**: One-click Kick, Ban, and Mute (supporting both temp and perm).
 - **Interactive Control**: 
   - Real-time Inventory Viewer.
   - Heal, Feed, and Teleport actions.
   - Dynamic Item Browser for giving items.
   - Gamemode switching (Adventure/Creative/Spectator).
 
 ### World & Environment
 - **Multi-World Support**: Track performance (TPS) per world.
 - **Atmosphere Control**: Instantly skip time or change weather.
 - **Warp System**: Manage server warps with ease.
 
 ### Advanced Tools
 - **Files & Backups**: Browse logs, create full server backups, and manage mod lists (with CurseForge integration).
 - **Web Config**: Hot-reload server settings (MOTD, Max Players) without downtime.
 - **Discord Integration**: Bridge your server with Discord for chat logs, alerts, and remote commands.
 
 ---
 
 ## Installation
 
 1. **Download**: Grab `AdminWebDash-1.0.1.jar` from the releases page.
 2. **Setup**: Place the JAR into your Hytale `mods` folder.
 3. **Launch**: Start your server. The console will display your **Admin Token** and **Dashboard URL**.
 4. **Access**: Navigate to `http://localhost:9081` (default).
 
> **Note:** If running in Docker, ensure port `9081` is mapped in your `docker-compose.yml`.
 
 ---
 
 ## Configuration
 
 Settings are stored in `mods/AdminWebDash/config.json`. The mod automatically generates this on first run.
 
 | Option | Description | Default |
 | :--- | :--- | :--- |
 | `port` | Web server port (set `0` for random) | `9081` |
 | `adminToken` | Secret key for dashboard access | `(Auto-generated)` |
 | `ipAllowlist` | List of IPs permitted to access the dashboard | `[]` (Allow All) |
 | `loginRateLimit` | Failed attempts before temporary lockout | `5` |
 | `backupInterval`| Auto-backup frequency in minutes (`0` = Off) | `0` |
 | `useHttps` | Enable SSL/TLS encryption | `false` |
 | `loggingEnabled`| Write dashboard activity to local logs | `false` |
 
 ---
 
 ## Security
 
 - **Authentication**: All API requests require the `X-Admin-Token` header.
 - **IP Protection**: Use `ipAllowlist` to restrict access to known administrative IPs.
 - **Brute-Force Prevention**: Automatic lockout after multiple failed login attempts.
 - **Encryption**: Built-in support for HTTPS and Let's Encrypt.
 
 ---
 
 ## Shortcuts
 
 | Key | Action |
 | :--- | :--- |
 | `Ctrl + F` | Focus Search |
 | `Ctrl + R` | Refresh Data |
 | `Esc` | Close Active Modal |
 | `Enter` | Submit Prompt/Confirm |
 
 ---
 
 ## License & Credits
 
 Created by **Patrick Jr.**  
 Distributed under the [MIT License](LICENSE.md).
 
 - **Discord**: [Join the Community](https://discord.gg/YUxuTa4ZzX)
 - **CurseForge**: [PatrickJr's Projects](https://www.curseforge.com/members/patrickjr/projects)
 
 *This mod is provided as-is for Hytale server administration.*
