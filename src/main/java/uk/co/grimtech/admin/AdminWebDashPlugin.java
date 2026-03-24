package uk.co.grimtech.admin;

import com.google.gson.JsonObject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import uk.co.grimtech.admin.web.ChatLog;
import uk.co.grimtech.admin.web.HytaleHttpServer;
import uk.co.grimtech.admin.web.DashboardAPI;
import uk.co.grimtech.admin.util.MuteTracker;
import uk.co.grimtech.admin.util.WarpManager;
import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.UUID;


import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.command.system.CommandSender;

public class AdminWebDashPlugin extends JavaPlugin {
    private static AdminWebDashPlugin instance;
    private static CustomLogger LOGGER;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static long startTime;
    private static String adminToken;
    private static boolean loggingEnabled = false; 
    private static int port = 9081; 
    private static int backupInterval = 0; 

    
    private static boolean discordEnabled = false;
    private static String discordToken = "";
    private static String discordGuildId = "";
    private static String discordChannelLogs = "";
    private static String discordChannelAlerts = "";
    private static String discordChannelJoins = "";
    private static String discordCommandPrefix = "!cmd ";
    
    private static boolean useHttps = false;
    private static String keystorePath = "keystore.jks";
    private static String keystorePassword = "";
    private static String domain = "";
    
    
    private static boolean reverseProxy = false;
    private static boolean letsEncrypt = false;
    private static String letsEncryptEmail = "";

    
    private static int loginRateLimit = 5;
    private static String logLevel = "INFO";
    private static java.util.List<String> ipAllowlist = new java.util.ArrayList<>();

    private HytaleHttpServer httpServer;
    private JDA jda;


    public static AdminWebDashPlugin getInstance() {
        return instance;
    }


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
    
    public int getBackupInterval() {
        return backupInterval;
    }

    public static boolean isDiscordEnabled() { return discordEnabled; }
    public static String getDiscordToken() { return discordToken; }
    public static String getDiscordGuildId() { return discordGuildId; }
    public static String getDiscordChannelLogs() { return discordChannelLogs; }
    public static String getDiscordChannelAlerts() { return discordChannelAlerts; }
    public static String getDiscordChannelJoins() { return discordChannelJoins; }
    public static String getDiscordCommandPrefix() { return discordCommandPrefix; }

    public static boolean useHttps() { return useHttps; }
    public static String getKeystorePath() { return keystorePath; }
    public static String getKeystorePassword() { return keystorePassword; }
    public static String getDomain() { return domain; }

    public static boolean isReverseProxy() { return reverseProxy; }
    public static boolean isLetsEncrypt() { return letsEncrypt; }
    public static String getLetsEncryptEmail() { return letsEncryptEmail; }

    public static int getLoginRateLimit() { return loginRateLimit; }
    public static String getLogLevel() { return logLevel; }
    public static java.util.List<String> getIpAllowlist() { return ipAllowlist; }

    public AdminWebDashPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        setupLogger();
        startTime = System.currentTimeMillis();
        LOGGER.info("[AdminWebDash] Starting Admin Dashboard Mod...");
        
        loadConfig();
        

        MuteTracker.load();
        WarpManager.load();
        
        if (letsEncrypt) {
            uk.co.grimtech.admin.util.LetsEncryptManager.startChallengeServer();
            uk.co.grimtech.admin.util.LetsEncryptManager.startRenewalTask();
        }

        if (discordEnabled && discordToken != null && !discordToken.isEmpty()) {
            try {
                jda = JDABuilder.createDefault(discordToken)
                        .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                        .addEventListeners(new DiscordListener())
                        .build();
                LOGGER.info("[AdminWebDash] Discord JDA Bot started!");
                
                new java.util.Timer().scheduleAtFixedRate(new java.util.TimerTask() {
                    @Override
                    public void run() {
                        try {
                            updateDiscordPresence();
                        } catch (Exception e) {
                            LOGGER.log("ERROR", "Failed to sync Discord presence", e);
                        }
                    }
                }, 5000, 30000); 
            } catch (Exception e) {
                LOGGER.severe("[AdminWebDash] Failed to initialize JDA Discord Bot: " + e.getMessage());
            }
        }

        try {
            httpServer = new HytaleHttpServer(port);
            httpServer.start();
            int actualPort = httpServer.getActualPort();
            LOGGER.info("[AdminWebDash] HTTP Server started on port " + actualPort);
            

            System.out.println("[AdminWebDash] ========================================");
            System.out.println("[AdminWebDash] Admin Token: " + adminToken);
            System.out.println("[AdminWebDash] Dashboard URL: http://localhost:" + actualPort);
            System.out.println("[AdminWebDash] ========================================");
            
            
            getEventRegistry().registerAsyncGlobal(PlayerChatEvent.class, future -> 
                future.thenApply(event -> {
                    if (!event.isCancelled()) {
                        
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
                            LOGGER.info("[AdminWebDash] Blocked chat from muted player: " + event.getSender().getUsername());
                        } else {
                            
                            ChatLog.addMessage(event.getSender().getUsername(), event.getContent());
                        }
                    }
                    return event;
                })
            );
        } catch (Exception e) {
            LOGGER.severe("[AdminWebDash] Failed to start HTTP Server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadConfig() {
        File dataDir = new File("mods/AdminWebDash");
        if (!dataDir.exists()) dataDir.mkdirs();

        File configFile = new File(dataDir, "config.json");
        try {
            if (configFile.exists()) {
                try (FileReader reader = new FileReader(configFile)) {
                    JsonObject config = GSON.fromJson(reader, JsonObject.class);
                    if (config.has("adminToken")) {
                        adminToken = config.get("adminToken").getAsString();
                    }
                    if (config.has("backupInterval")) {
                        backupInterval = config.get("backupInterval").getAsInt();
                    }
                    if (config.has("loggingEnabled")) {
                        loggingEnabled = config.get("loggingEnabled").getAsBoolean();
                    }
                    if (config.has("port")) {
                        int configPort = config.get("port").getAsInt();
                        if (configPort == 0) {
                            port = 0;
                        } else {
                            port = configPort;
                            if (port < 1024 || port > 65535) {
                                LOGGER.warning("[AdminWebDash] Invalid port " + port + " in config, using default 9081");
                                port = 9081;
                            }
                        }
                    }
                    if (config.has("discordEnabled")) discordEnabled = config.get("discordEnabled").getAsBoolean();
                    if (config.has("discordToken")) discordToken = config.get("discordToken").getAsString();
                    if (config.has("discordGuildId")) discordGuildId = config.get("discordGuildId").getAsString();
                    if (config.has("discordChannelLogs")) discordChannelLogs = config.get("discordChannelLogs").getAsString();
                    if (config.has("discordChannelAlerts")) discordChannelAlerts = config.get("discordChannelAlerts").getAsString();
                    if (config.has("discordChannelJoins")) discordChannelJoins = config.get("discordChannelJoins").getAsString();
                    if (config.has("discordCommandPrefix")) discordCommandPrefix = config.get("discordCommandPrefix").getAsString();
                    
                    if (config.has("useHttps")) useHttps = config.get("useHttps").getAsBoolean();
                    if (config.has("keystorePath")) keystorePath = config.get("keystorePath").getAsString();
                    if (config.has("keystorePassword")) keystorePassword = config.get("keystorePassword").getAsString();
                    if (config.has("domain")) domain = config.get("domain").getAsString();

                    if (config.has("reverseProxy")) reverseProxy = config.get("reverseProxy").getAsBoolean();
                    if (config.has("letsEncrypt")) letsEncrypt = config.get("letsEncrypt").getAsBoolean();
                    if (config.has("letsEncryptEmail")) letsEncryptEmail = config.get("letsEncryptEmail").getAsString();

                    if (config.has("loginRateLimit")) loginRateLimit = config.get("loginRateLimit").getAsInt();
                    if (config.has("logLevel")) logLevel = config.get("logLevel").getAsString();
                    if (config.has("ipAllowlist")) {
                        com.google.gson.JsonArray arr = config.getAsJsonArray("ipAllowlist");
                        for (com.google.gson.JsonElement el : arr) {
                            ipAllowlist.add(el.getAsString());
                        }
                    }

                }
            }

            if (adminToken == null || adminToken.isEmpty()) {
                adminToken = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            }
            if (keystorePassword == null || keystorePassword.isEmpty()) {
                keystorePassword = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            }
            
            JsonObject config = new JsonObject();
            config.addProperty("port", port);
            config.addProperty("backupInterval", backupInterval);
            config.addProperty("adminToken", adminToken);
            config.addProperty("loggingEnabled", loggingEnabled);
            
            config.addProperty("discordEnabled", discordEnabled);
            config.addProperty("discordToken", discordToken);
            config.addProperty("discordGuildId", discordGuildId);
            config.addProperty("discordChannelLogs", discordChannelLogs);
            config.addProperty("discordChannelAlerts", discordChannelAlerts);
            config.addProperty("discordChannelJoins", discordChannelJoins);
            config.addProperty("discordCommandPrefix", discordCommandPrefix);
            
            config.addProperty("useHttps", useHttps);
            config.addProperty("keystorePath", keystorePath);
            config.addProperty("keystorePassword", keystorePassword);
            config.addProperty("domain", domain);

            config.addProperty("reverseProxy", reverseProxy);
            config.addProperty("letsEncrypt", letsEncrypt);
            config.addProperty("letsEncryptEmail", letsEncryptEmail);
            config.addProperty("loginRateLimit", loginRateLimit);
            config.addProperty("logLevel", logLevel);

            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            for (String ip : ipAllowlist) {
                arr.add(ip);
            }
            config.add("ipAllowlist", arr);

            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(config, writer);
            }
            
            if (LOGGER != null) {
                LOGGER.info("[AdminWebDash] Config loaded - Port: " + (port == 0 ? "random" : port) + ", Logging enabled: " + loggingEnabled);
                LOGGER.info("[AdminWebDash] ========================================");
                LOGGER.info("[AdminWebDash] Admin Token: " + adminToken);
                LOGGER.info("[AdminWebDash] Use this token to log into the dashboard");
                LOGGER.info("[AdminWebDash] ========================================");
            }
            
        } catch (Exception e) {
            if (LOGGER != null) {
                LOGGER.severe("[AdminWebDash] Failed to load/save config: " + e.getMessage());
            }
        }
    }

    private void setupLogger() {
        try {
            File logFile = new File("logs/dashboard.log").getAbsoluteFile();
            File logDir = logFile.getParentFile();
            if (!logDir.exists()) logDir.mkdirs();
            LOGGER = new CustomLogger(logFile.getAbsolutePath());
            
            LOGGER.info("[AdminWebDash] Custom logging initialized to: " + logFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("[AdminWebDash] Failed to initialize file logger: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void updateBackupInterval(int interval) {
        backupInterval = interval;
        
        
        try {
            File dataDir = new File("mods/AdminWebDash");
            if (!dataDir.exists()) dataDir.mkdirs();
            File configFile = new File(dataDir, "config.json");
            
            JsonObject config = new JsonObject();
            config.addProperty("port", port);
            config.addProperty("backupInterval", backupInterval);
            config.addProperty("adminToken", adminToken);
            config.addProperty("loggingEnabled", loggingEnabled);
            
            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(config, writer);
            }
            LOGGER.info("[AdminWebDash] Backup interval updated to " + interval + " and saved to config.");
        } catch (Exception e) {
            LOGGER.severe("[AdminWebDash] Failed to save config: " + e.getMessage());
        }
    }

    @Override
    protected void shutdown() {
        uk.co.grimtech.admin.util.LetsEncryptManager.stopChallengeServer();
        if (httpServer != null) {
            httpServer.stop();
            LOGGER.info("[AdminWebDash] HTTP Server stopped.");
        }
        if (jda != null) {
            jda.shutdown();
            LOGGER.info("[AdminWebDash] Discord JDA Bot stopped.");
        }
    }
    
    private void updateDiscordPresence() {
        if (jda == null) return;
        
        try {
            int onlinePlayers = com.hypixel.hytale.server.core.universe.Universe.get().getPlayers().size();
            jda.getPresence().setActivity(Activity.watching(onlinePlayers + " players"));
        } catch (Exception e) {
            LOGGER.warning("[AdminWebDash] Failed to sync presence to JDA: " + e.getMessage());
        }
    }
    
    
    private class DiscordListener extends ListenerAdapter {
        @Override
        public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
            if (event.getAuthor().isBot()) return;
            
            String channelId = event.getChannel().getId();
            
            String content = event.getMessage().getContentRaw();
            if (content.startsWith(discordCommandPrefix) && (discordChannelLogs.isEmpty() || channelId.equals(discordChannelLogs))) {
                String cmd = content.substring(discordCommandPrefix.length()).trim();
                if (cmd.startsWith("/")) cmd = cmd.substring(1);
                
                DiscordCommandSender sender = new DiscordCommandSender(event.getChannel());
                
                try {
                    String[] args = cmd.split(" ");
                    String action = args[0].toLowerCase();
                    boolean isCustom = false;
                    
                    if (args.length >= 2) {
                        if (action.equals("heal") || action.equals("kick") || action.equals("ban") || 
                            action.equals("gamemode") || action.equals("give") || action.equals("mute") || 
                            action.equals("unmute") || action.equals("unban") || action.equals("clearinv")) {
                            
                            isCustom = true;
                            handleCustomCommand(cmd, args, action, sender, event.getChannel());
                        }
                    } 
                    if (action.equals("time") || action.equals("weather") || action.equals("help")) {
                        isCustom = true;
                        handleCustomCommand(cmd, args, action, sender, event.getChannel());
                    }
                    
                    if (!isCustom) {
                        
                        CommandManager.get().handleCommand((CommandSender) sender, cmd).whenComplete((result, exception) -> {
                            if (exception != null) {
                                sender.sendMessage(com.hypixel.hytale.server.core.Message.raw("❌ Execution Exception: " + exception.getMessage()));
                            }
                            
                            
                            
                            new java.util.Timer().schedule(new java.util.TimerTask() {
                                @Override
                                public void run() {
                                    sender.flush();
                                }
                            }, 1000); 
                        });
                    }
                } catch (Exception e) {
                    event.getChannel().sendMessage("❌ Error dispatching command: " + e.getMessage()).queue();
                }
            }
        }

        private void handleCustomCommand(String cmd, String[] args, String action, DiscordCommandSender sender, net.dv8tion.jda.api.entities.channel.middleman.MessageChannel channel) {
            
            if (action.equals("help")) {
                StringBuilder helpMsg = new StringBuilder();
                helpMsg.append("**== Discord Dashboard Commands ==**\n");
                helpMsg.append("`").append(discordCommandPrefix).append("time <morning/noon/evening/night/0-24000>`\n");
                helpMsg.append("`").append(discordCommandPrefix).append("weather <clear/rain/storm/snow>`\n");
                helpMsg.append("`").append(discordCommandPrefix).append("heal <player>`\n");
                helpMsg.append("`").append(discordCommandPrefix).append("gamemode <player> <Creative/Adventure/Spectator>`\n");
                helpMsg.append("`").append(discordCommandPrefix).append("give <player> <item_id> [count]`\n");
                helpMsg.append("`").append(discordCommandPrefix).append("clearinv <player>`\n");
                helpMsg.append("`").append(discordCommandPrefix).append("kick <player> [reason]`\n");
                helpMsg.append("`").append(discordCommandPrefix).append("ban <player> [reason]`\n");
                helpMsg.append("`").append(discordCommandPrefix).append("unban <player uuid>`\n");
                helpMsg.append("`").append(discordCommandPrefix).append("mute <player> [reason]`\n");
                helpMsg.append("`").append(discordCommandPrefix).append("unmute <player>`\n\n");
                
                String finalMessage = helpMsg.toString();
                if (finalMessage.length() > 1900) {
                    finalMessage = finalMessage.substring(0, 1900) + "... [truncated]";
                }
                channel.sendMessage(finalMessage).queue();
                return;
            }

            
            if (action.equals("time")) {
                if (args.length >= 2) {
                    JsonObject payload = new JsonObject();
                    payload.addProperty("time", args[1]);
                    String res = DashboardAPI.setTime(GSON.toJson(payload));
                    sendApiResponse(channel, res);
                } else {
                    channel.sendMessage("Usage: " + discordCommandPrefix + "time <morning/noon/evening/night/0-24000>").queue();
                }
                return;
            }
            if (action.equals("weather")) {
                if (args.length >= 2) {
                    JsonObject payload = new JsonObject();
                    payload.addProperty("weather", args[1]);
                    String res = DashboardAPI.setWeather(GSON.toJson(payload));
                    sendApiResponse(channel, res);
                } else {
                    channel.sendMessage("Usage: " + discordCommandPrefix + "weather <clear/rain/storm/snow>").queue();
                }
                return;
            }

            if (args.length < 2) {
                channel.sendMessage("Usage: " + discordCommandPrefix + "<action> <player> [args]").queue();
                return;
            }
            
            String playerName = args[1];
            String uuid = null;
            
            
            java.util.List<com.hypixel.hytale.server.core.universe.PlayerRef> players = com.hypixel.hytale.server.core.universe.Universe.get().getPlayers();
            for (com.hypixel.hytale.server.core.universe.PlayerRef ref : players) {
                if (ref.getUsername().equalsIgnoreCase(playerName)) {
                    uuid = ref.getUuid().toString();
                    break;
                }
            }
            
            if (uuid == null) {
                channel.sendMessage("Player not found online: " + playerName).queue();
                return;
            }
            
            JsonObject payload = new JsonObject();
            payload.addProperty("uuid", uuid);
            String response = "";
            
            switch (action) {
                case "heal":
                    response = DashboardAPI.healPlayer(GSON.toJson(payload));
                    break;
                case "clearinv":
                    response = DashboardAPI.clearInventory(GSON.toJson(payload));
                    break;
                case "kick":
                    payload.addProperty("reason", args.length > 2 ? cmd.substring(action.length() + playerName.length() + 2).trim() : "Kicked via Discord");
                    response = DashboardAPI.kickPlayer(GSON.toJson(payload));
                    break;
                case "ban":
                    payload.addProperty("reason", args.length > 2 ? cmd.substring(action.length() + playerName.length() + 2).trim() : "Banned via Discord");
                    response = DashboardAPI.banPlayer(GSON.toJson(payload));
                    break;
                case "unban":
                    response = DashboardAPI.unbanPlayer(GSON.toJson(payload));
                    break;
                case "mute":
                    payload.addProperty("reason", args.length > 2 ? cmd.substring(action.length() + playerName.length() + 2).trim() : "Muted via Discord");
                    response = DashboardAPI.mutePlayer(GSON.toJson(payload));
                    break;
                case "unmute":
                    response = DashboardAPI.unmutePlayer(GSON.toJson(payload));
                    break;
                case "gamemode":
                    if (args.length > 2) {
                        payload.addProperty("gamemode", args[2]);
                        response = DashboardAPI.setGamemode(GSON.toJson(payload));
                    } else {
                        channel.sendMessage("Usage: " + discordCommandPrefix + "gamemode <player> <Creative/Adventure/Spectator>").queue();
                        return;
                    }
                    break;
                case "give":
                    if (args.length > 2) {
                        payload.addProperty("item", args[2]);
                        payload.addProperty("count", args.length > 3 ? Integer.parseInt(args[3]) : 1);
                        response = DashboardAPI.giveItem(GSON.toJson(payload));
                    } else {
                        channel.sendMessage("Usage: " + discordCommandPrefix + "give <player> <item_id> [count]").queue();
                        return;
                    }
                    break;
                default:
                    channel.sendMessage("Known custom command not handled: " + action).queue();
                    return;
            }
            sendApiResponse(channel, response);
        }

        private void sendApiResponse(net.dv8tion.jda.api.entities.channel.middleman.MessageChannel channel, String response) {
            try {
                JsonObject result = GSON.fromJson(response, JsonObject.class);
                if (result.has("status") && result.get("status").getAsString().equals("success")) {
                    channel.sendMessage("✅ Command executed successfully.").queue();
                } else if (result.has("error")) {
                    channel.sendMessage("❌ Error: " + result.get("error").getAsString()).queue();
                } else {
                    channel.sendMessage("Result: " + response).queue();
                }
            } catch (Exception e) {
                channel.sendMessage("Result: " + response).queue();
            }
        }
    }

    private class DiscordCommandSender implements CommandSender {
        private final net.dv8tion.jda.api.entities.channel.middleman.MessageChannel channel;
        private final StringBuilder output = new StringBuilder();

        public DiscordCommandSender(net.dv8tion.jda.api.entities.channel.middleman.MessageChannel channel) {
            this.channel = channel;
        }

        @Override
        public void sendMessage(@Nonnull com.hypixel.hytale.server.core.Message message) {
            String text = com.hypixel.hytale.server.core.util.MessageUtil.toAnsiString(message).toString();
            if (text != null && !text.isEmpty()) {
                synchronized (output) {
                    output.append(text).append("\n");
                }
            }
        }

        @Override
        @Nonnull
        public String getDisplayName() {
            return "Discord";
        }

        @Override
        @Nonnull
        public UUID getUuid() {
            return new UUID(0, 0); 
        }

        @Override
        public boolean hasPermission(@Nonnull String id) {
            return true; 
        }

        @Override
        public boolean hasPermission(@Nonnull String id, boolean def) {
            return true;
        }

        public void flush() {
            synchronized (output) {
                if (output.length() > 0) {
                    
                    String text = output.toString().replaceAll("<[^>]*>", "");
                    
                    if (text.length() > 1900) {
                        text = text.substring(0, 1900) + "\n... [TRUNCATED]";
                    }
                    channel.sendMessage("```\n" + text + "```").queue();
                    output.setLength(0);
                } else {
                    channel.sendMessage("✅ Command executed (no output).").queue();
                }
            }
        }
    }
}
