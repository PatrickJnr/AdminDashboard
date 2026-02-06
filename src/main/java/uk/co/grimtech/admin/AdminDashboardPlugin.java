package uk.co.grimtech.admin;

import com.google.gson.JsonObject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import uk.co.grimtech.admin.web.ChatLog;
import uk.co.grimtech.admin.web.HytaleHttpServer;
import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.UUID;

public class AdminDashboardPlugin extends JavaPlugin {
    private static CustomLogger LOGGER;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static long startTime;
    private static String adminToken;
    private static boolean loggingEnabled = true;
    private HytaleHttpServer httpServer;

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
    }

    @Override
    protected void setup() {
        setupLogger();
        startTime = System.currentTimeMillis();
        LOGGER.info("[AdminDashboard] Starting Admin Dashboard Mod...");
        
        loadConfig();

        try {
            int port = 9081;
            httpServer = new HytaleHttpServer(port);
            httpServer.start();
            LOGGER.info("[AdminDashboard] HTTP Server started on port " + port);
            
            // Register Chat Listener using registerAsyncGlobal for IAsyncEvent
            getEventRegistry().registerAsyncGlobal(PlayerChatEvent.class, future -> 
                future.thenApply(event -> {
                    if (!event.isCancelled()) {
                        ChatLog.addMessage(event.getSender().getUsername(), event.getContent());
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
                }
            }

            // Generate token if needed
            if (adminToken == null || adminToken.isEmpty()) {
                adminToken = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            }
            
            // Save config with both settings
            JsonObject config = new JsonObject();
            config.addProperty("adminToken", adminToken);
            config.addProperty("loggingEnabled", loggingEnabled);
            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(config, writer);
            }
            
            if (LOGGER != null) {
                LOGGER.info("[AdminDashboard] Config loaded - Logging enabled: " + loggingEnabled);
            }
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
