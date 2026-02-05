package uk.co.grimtech.admin;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import uk.co.grimtech.admin.web.HytaleHttpServer;
import javax.annotation.Nonnull;
import java.util.logging.Logger;

public class AdminDashboardPlugin extends JavaPlugin {
    private static final Logger LOGGER = Logger.getLogger("AdminDashboard");
    private HytaleHttpServer httpServer;

    public AdminDashboardPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        LOGGER.info("[AdminDashboard] Starting Admin Dashboard Mod...");
        
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

    @Override
    protected void shutdown() {
        if (httpServer != null) {
            httpServer.stop();
            LOGGER.info("[AdminDashboard] HTTP Server stopped.");
        }
    }
}
