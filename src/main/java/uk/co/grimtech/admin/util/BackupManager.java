package uk.co.grimtech.admin.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Options;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.util.backup.BackupTask;
import uk.co.grimtech.admin.AdminWebDashPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class BackupManager {
    private static final Gson GSON = new Gson();
    private static Path backupDirectory;
    private static ScheduledFuture<?> scheduledBackupTask;

    static {
        // Resolve backup directory
        if (Options.getOptionSet().has(Options.BACKUP_DIRECTORY)) {
            backupDirectory = Options.getOptionSet().valueOf(Options.BACKUP_DIRECTORY);
        } else {
            backupDirectory = Paths.get("Backups");
        }
        
        // Ensure directory exists
        try {
            if (!Files.exists(backupDirectory)) {
                Files.createDirectories(backupDirectory);
            }
        } catch (IOException e) {
            AdminWebDashPlugin.getCustomLogger().log("ERROR", "Failed to create backup directory", e);
        }
    }

    public static String getBackups() {
        JsonArray backups = new JsonArray();
        try {
            if (!Files.exists(backupDirectory)) {
                return GSON.toJson(backups);
            }

            List<Path> files = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupDirectory, "*.zip")) {
                for (Path entry : stream) {
                    files.add(entry);
                }
            }

            files.sort((p1, p2) -> {
                try {
                    return Files.getLastModifiedTime(p2).compareTo(Files.getLastModifiedTime(p1));
                } catch (IOException e) {
                    return 0;
                }
            });

            for (Path path : files) {
                JsonObject obj = new JsonObject();
                obj.addProperty("name", path.getFileName().toString());
                obj.addProperty("size", Files.size(path));
                obj.addProperty("timestamp", Files.getLastModifiedTime(path).toMillis());
                backups.add(obj);
            }

        } catch (Exception e) {
            AdminWebDashPlugin.getCustomLogger().log("ERROR", "Failed to list backups", e);
        }
        return GSON.toJson(backups);
    }

    public static String createBackup() {
        try {
            AdminWebDashPlugin.getCustomLogger().info("Starting manual backup...");
            BackupTask.start(Universe.get().getPath(), backupDirectory).join();
            AdminWebDashPlugin.getCustomLogger().info("Manual backup completed.");
            return "{\"status\": \"success\"}";
        } catch (Exception e) {
            AdminWebDashPlugin.getCustomLogger().log("ERROR", "Backup failed", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    public static String restoreBackup(String fileName) {
        Path backupFile = backupDirectory.resolve(fileName);
        if (!Files.exists(backupFile)) {
            return "{\"error\": \"Backup file not found\"}";
        }

        AdminWebDashPlugin.getCustomLogger().info("Initiating restore from: " + fileName);

        CompletableFuture.runAsync(() -> {
            try {
                // 1. Kick players
                Universe.get().disconnectAllPLayers();
                AdminWebDashPlugin.getCustomLogger().info("Restoring: All players kicked.");
                
                // 2. Unload worlds
                Universe.get().shutdownAllWorlds();
                AdminWebDashPlugin.getCustomLogger().info("Restoring: All worlds stopped.");
                
                // Wait for file handles to release
                try { Thread.sleep(3000); } catch (InterruptedException e) {}

                // 3. Unzip backup to Universe directory
                Path universePath = Universe.get().getPath();
                AdminWebDashPlugin.getCustomLogger().info("Restoring: Unzipping to " + universePath);
                unzip(backupFile, universePath);

                // 4. Reload Default World
                String defaultWorld = HytaleServer.get().getConfig().getDefaults().getWorld();
                if (defaultWorld != null) {
                     AdminWebDashPlugin.getCustomLogger().info("Restoring: Reloading world " + defaultWorld);
                    Universe.get().loadWorld(defaultWorld).join();
                }

                AdminWebDashPlugin.getCustomLogger().info("Restoring: Restore completed successfully.");
                
            } catch (Exception e) {
                AdminWebDashPlugin.getCustomLogger().log("SEVERE", "Restore failed", e);
            }
        });

        return "{\"status\": \"restore_started\"}";
    }
    
    public static void setSchedule(int interval) {
        if (scheduledBackupTask != null) {
            scheduledBackupTask.cancel(false);
            scheduledBackupTask = null;
        }
        
        if (interval > 0) {
            scheduledBackupTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(() -> {
                try {
                    AdminWebDashPlugin.getCustomLogger().info("Running scheduled backup...");
                    BackupTask.start(Universe.get().getPath(), backupDirectory).join();
                } catch (Exception e) {
                    AdminWebDashPlugin.getCustomLogger().log("ERROR", "Scheduled backup failed", e);
                }
            }, interval, interval, TimeUnit.MINUTES);
            AdminWebDashPlugin.getCustomLogger().info("Scheduled backups every " + interval + " minutes.");
        } else {
            AdminWebDashPlugin.getCustomLogger().info("Scheduled backups disabled.");
        }
    }

    public static String scheduleBackups(String body) {
        try {
            JsonObject json = GSON.fromJson(body, JsonObject.class);
            if (!json.has("intervalMinutes")) return "{\"error\": \"Missing intervalMinutes\"}";
            
            int interval = json.get("intervalMinutes").getAsInt();
            setSchedule(interval);
            
            return "{\"status\": \"success\"}";
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
    
    public static String deleteBackup(String body) {
        try {
            JsonObject json = GSON.fromJson(body, JsonObject.class);
            if (!json.has("name")) return "{\"error\": \"Missing name\"}";
            
            String fileName = json.get("name").getAsString();
            Path backupFile = backupDirectory.resolve(fileName);
            
            // Security check: ensure we are only deleting files in the backup directory
            if (!backupFile.normalize().startsWith(backupDirectory.normalize())) {
                return "{\"error\": \"Invalid file path\"}";
            }
            
            if (Files.exists(backupFile)) {
                Files.delete(backupFile);
                return "{\"status\": \"success\"}";
            } else {
                return "{\"error\": \"File not found\"}";
            }
        } catch (Exception e) {
             return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private static void unzip(Path zipFile, Path targetDir) throws IOException {
        try (InputStream fis = Files.newInputStream(zipFile);
             ZipInputStream zis = new ZipInputStream(fis)) {
            
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path newPath = targetDir.resolve(entry.getName());
                
                // Protection against Zip Slip
                if (!newPath.normalize().startsWith(targetDir.normalize())) {
                    continue;
                }
                
                if (entry.isDirectory()) {
                    Files.createDirectories(newPath);
                } else {
                    if (newPath.getParent() != null && !Files.exists(newPath.getParent())) {
                        Files.createDirectories(newPath.getParent());
                    }
                    Files.copy(zis, newPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
