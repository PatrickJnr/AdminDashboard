package uk.co.grimtech.admin;

import com.google.gson.JsonObject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import uk.co.grimtech.admin.web.ChatLog;
import uk.co.grimtech.admin.web.HytaleHttpServer;
import uk.co.grimtech.admin.util.MuteTracker;
import uk.co.grimtech.admin.util.WarpManager;
import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.UUID;

public class AdminDashboardPlugin extends JavaPlugin {
    private static AdminDashboardPlugin instance;
    private static CustomLogger LOGGER;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static long startTime;
    private static String adminToken;
    private static boolean loggingEnabled = false; // Default to false for production
    private static int port = 9081; // Default port
    private HytaleHttpServer httpServer;

    // Public getter for plugin instance
    public static AdminDashboardPlugin getInstance() {
        return instance;
    }

    // Public getter for other classes to use the configured logger
    public static CustomLogger getCustomLogger() {
        return LOGGER;
    }
    
    public static boolean isLoggingEnabled() {
        return loggingEnabled;
    }

    public static long getStartTime() {
        return startTime;
    }

    public static String getAdminToken() {
        return adminToken;
    }

    public AdminDashboardPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        setupLogger();
        startTime = System.currentTimeMillis();
        LOGGER.info("[AdminDashboard] Starting Admin Dashboard Mod...");
        
        loadConfig();
        
        // Load data trackers
        MuteTracker.load();
        WarpManager.load();
        LOGGER.info("[AdminDashboard] Data trackers initialized");

        try {
            httpServer = new HytaleHttpServer(port);
            httpServer.start();
            int actualPort = httpServer.getActualPort();
            LOGGER.info("[AdminDashboard] HTTP Server started on port " + actualPort);
            
            // Print to console with actual port
            System.out.println("[AdminDashboard] ========================================");
            System.out.println("[AdminDashboard] Admin Token: " + adminToken);
            System.out.println("[AdminDashboard] Dashboard URL: http://localhost:" + actualPort);
            System.out.println("[AdminDashboard] ========================================");
            
            // Register Chat Listener using registerAsyncGlobal for IAsyncEvent
            getEventRegistry().registerAsyncGlobal(PlayerChatEvent.class, future -> 
                future.thenApply(event -> {
                    if (!event.isCancelled()) {
                        // Check if player is muted
                        UUID playerUuid = event.getSender().getUuid();
                        if (MuteTracker.isMuted(playerUuid)) {
                            event.setCancelled(true);
                            MuteTracker.Mute mute = MuteTracker.getMute(playerUuid);
                            if (mute != null) {
                                String muteMsg;
                                if (mute.durationSeconds == null) {
                                    muteMsg = "You are permanently muted. Reason: " + mute.reason;
                                } else {
                                    long remaining = mute.getRemainingSeconds();
                                    long minutes = remaining / 60;
                                    long seconds = remaining % 60;
                                    muteMsg = String.format("You are muted for %dm %ds. Reason: %s", 
                                        minutes, seconds, mute.reason);
                                }
                                event.getSender().sendMessage(com.hypixel.hytale.server.core.Message.raw(muteMsg));
                            }
                            LOGGER.info("[AdminDashboard] Blocked chat from muted player: " + event.getSender().getUsername());
                        } else {
                            // Only log if not muted
                            ChatLog.addMessage(event.getSender().getUsername(), event.getContent());
                        }
                    }
                    return event;
                })
            );
        } catch (Exception e) {
            LOGGER.severe("[AdminDashboard] Failed to start HTTP Server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadConfig() {
        File dataDir = new File("mods/AdminDashboard");
        if (!dataDir.exists()) dataDir.mkdirs();

        File configFile = new File(dataDir, "config.json");
        try {
            if (configFile.exists()) {
                try (FileReader reader = new FileReader(configFile)) {
                    JsonObject config = GSON.fromJson(reader, JsonObject.class);
                    if (config.has("adminToken")) {
                        adminToken = config.get("adminToken").getAsString();
                    }
                    if (config.has("loggingEnabled")) {
                        loggingEnabled = config.get("loggingEnabled").getAsBoolean();
                    }
                    if (config.has("port")) {
                        // Check if port is set to 0 (random port)
                        int configPort = config.get("port").getAsInt();
                        if (configPort == 0) {
                            // Use random available port
                            port = 0;
                        } else {
                            port = configPort;
                            // Validate port range
                            if (port < 1024 || port > 65535) {
                                LOGGER.warning("[AdminDashboard] Invalid port " + port + " in config, using default 9081");
                                port = 9081;
                            }
                        }
                    }
                }
            }

            // Generate token if needed
            if (adminToken == null || adminToken.isEmpty()) {
                adminToken = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            }
            
            // Save config with all settings
            JsonObject config = new JsonObject();
            config.addProperty("port", port);
            config.addProperty("adminToken", adminToken);
            config.addProperty("loggingEnabled", loggingEnabled);
            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(config, writer);
            }
            
            if (LOGGER != null) {
                LOGGER.info("[AdminDashboard] Config loaded - Port: " + (port == 0 ? "random" : port) + ", Logging enabled: " + loggingEnabled);
                LOGGER.info("[AdminDashboard] ========================================");
                LOGGER.info("[AdminDashboard] Admin Token: " + adminToken);
                LOGGER.info("[AdminDashboard] Use this token to log into the dashboard");
                LOGGER.info("[AdminDashboard] ========================================");
            }
            
            // Note: Actual port will be printed after server starts if using random port
        } catch (Exception e) {
            if (LOGGER != null) {
                LOGGER.severe("[AdminDashboard] Failed to load/save config: " + e.getMessage());
            }
        }
    }

    private void setupLogger() {
        try {
            // Use absolute path to ensure we write to the correct location
            File logFile = new File("logs/dashboard.log").getAbsoluteFile();
            File logDir = logFile.getParentFile();
            if (!logDir.exists()) logDir.mkdirs();
            
            // Create custom logger that writes directly to file
            LOGGER = new CustomLogger(logFile.getAbsolutePath());
            
            // Test write
            LOGGER.info("[AdminDashboard] Custom logging initialized to: " + logFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("[AdminDashboard] Failed to initialize file logger: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    protected void shutdown() {
        if (httpServer != null) {
            httpServer.stop();
            LOGGER.info("[AdminDashboard] HTTP Server stopped.");
        }
    }
}
