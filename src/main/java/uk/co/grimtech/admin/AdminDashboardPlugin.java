package uk.co.grimtech.admin;

import com.google.gson.JsonObject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import uk.co.grimtech.admin.web.HytaleHttpServer;
import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.UUID;
import java.util.logging.Logger;

public class AdminDashboardPlugin extends JavaPlugin {
    private static final Logger LOGGER = Logger.getLogger("AdminDashboard");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
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
        startTime = System.currentTimeMillis();
        LOGGER.info("[AdminDashboard] Starting Admin Dashboard Mod...");
        
        loadConfig();

        try {
            int port = 9081;
            httpServer = new HytaleHttpServer(port);
            httpServer.start();
            LOGGER.info("[AdminDashboard] HTTP Server started on port " + port);
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
                LOGGER.info("[AdminDashboard] Generated new admin token: " + adminToken);
            }
        } catch (Exception e) {
            LOGGER.severe("[AdminDashboard] Failed to load/save config: " + e.getMessage());
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
