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
import java.util.logging.Logger;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;
import java.util.logging.Level;
import java.util.logging.Handler;

public class AdminDashboardPlugin extends JavaPlugin {
    private static final Logger LOGGER = Logger.getLogger("AdminDebug");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static FileHandler fileHandler;
    private static long startTime;
    private static String adminToken;
    private HytaleHttpServer httpServer;

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
                }
            }

            if (adminToken == null || adminToken.isEmpty()) {
                adminToken = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
                JsonObject config = new JsonObject();
                config.addProperty("adminToken", adminToken);
                try (FileWriter writer = new FileWriter(configFile)) {
                    GSON.toJson(config, writer);
                }
            }
        } catch (Exception e) {
            LOGGER.severe("[AdminDashboard] Failed to load/save config: " + e.getMessage());
        }
    }

    private void setupLogger() {
        try {
            File logDir = new File("logs");
            if (!logDir.exists()) logDir.mkdirs();
            
            File logFile = new File(logDir, "dashboard.log");
            fileHandler = new FileHandler(logFile.getAbsolutePath(), true);
            fileHandler.setFormatter(new SimpleFormatter());
            fileHandler.setLevel(Level.ALL);
            LOGGER.setLevel(Level.ALL);
            
            // Add to the main logger
            LOGGER.addHandler(fileHandler);
            LOGGER.setUseParentHandlers(false); // Stop spamming server.log
            
            // Force an immediate write to test
            LOGGER.info("[AdminDebug] Logger initialized and file handler attached.");
            fileHandler.flush();
            
            // Still log to console that logging has moved
            Logger.getLogger("Global").info("[AdminDebug] Dedicated logging started in: " + logFile.getAbsolutePath());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[AdminDashboard] Failed to initialize file logger", e);
        }
    }

    @Override
    protected void shutdown() {
        if (httpServer != null) {
            httpServer.stop();
            LOGGER.info("[AdminDashboard] HTTP Server stopped.");
        }
        
        if (fileHandler != null) {
            fileHandler.close();
            LOGGER.removeHandler(fileHandler);
        }
    }
}
