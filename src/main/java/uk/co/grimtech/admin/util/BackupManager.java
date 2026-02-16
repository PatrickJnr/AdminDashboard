package uk.co.grimtech.admin.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Options;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.util.backup.BackupTask;
import uk.co.grimtech.admin.AdminWebDashPlugin;

import java.io.File;
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

        if (Options.getOptionSet().has(Options.BACKUP_DIRECTORY)) {
            backupDirectory = Options.getOptionSet().valueOf(Options.BACKUP_DIRECTORY);
        } else {
            backupDirectory = Paths.get("Backups");
        }
        

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


    private static final java.util.concurrent.atomic.AtomicInteger totalFiles = new java.util.concurrent.atomic.AtomicInteger(0);
    private static final java.util.concurrent.atomic.AtomicInteger processedFiles = new java.util.concurrent.atomic.AtomicInteger(0);
    private static volatile boolean isBackingUp = false;
    private static volatile String currentStatusMessage = "";

    public static String getBackupStatus() {
        JsonObject status = new JsonObject();
        boolean active = isBackingUp;
        status.addProperty("active", active);
        
        if (active) {
            int total = totalFiles.get();
            int processed = processedFiles.get();
            double progress = total > 0 ? (double) processed / total : 0;
            
            status.addProperty("progress", progress);
            status.addProperty("processed", processed);
            status.addProperty("total", total);
            status.addProperty("message", currentStatusMessage);
        } else {
            status.addProperty("progress", 0);
            status.addProperty("message", currentStatusMessage.isEmpty() ? "Idle" : currentStatusMessage);
        }
        
        return GSON.toJson(status);
    }

    private static final int MAX_BACKUPS = 10;
    private static final long MIN_DISK_SPACE_BYTES = 500 * 1024 * 1024; // 500 MB

    public static String createBackup() {
        if (isBackingUp) {
            return "{\"error\": \"Backup already in progress\"}";
        }
        
        try {

            File backupDirFile = backupDirectory.toFile();
            if (backupDirFile.exists() && backupDirFile.getUsableSpace() < MIN_DISK_SPACE_BYTES) {
                 return "{\"error\": \"Not enough disk space. Requires at least 500MB free.\"}";
            }
            
            AdminWebDashPlugin.getCustomLogger().info("Starting manual backup...");
            AdminWebDashPlugin.getCustomLogger().info("WARN: Performing Live Backup. World data is not paused/flushed.");
            
            isBackingUp = true;
            currentStatusMessage = "Initializing...";
            processedFiles.set(0);
            totalFiles.set(0);
            
            String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new java.util.Date());
            String fileName = timestamp + ".zip";
            String tempFileName = fileName + ".tmp";
            
            Path backupFile = backupDirectory.resolve(fileName);
            Path tempFile = backupDirectory.resolve(tempFileName);
            
            Path sourceDir = Universe.get().getPath();
            
            CompletableFuture.runAsync(() -> {
                try {
                    currentStatusMessage = "Scanning files...";
                    totalFiles.set(countFiles(sourceDir));
                    currentStatusMessage = "Backing up...";
                    
                    zipDirectory(sourceDir, tempFile);
                    Files.move(tempFile, backupFile);
                    

                    cleanupOldBackups();
                    
                    AdminWebDashPlugin.getCustomLogger().info("Manual backup completed: " + fileName);
                    currentStatusMessage = "Completed";
                } catch (Exception e) {
                    AdminWebDashPlugin.getCustomLogger().log("ERROR", "Backup failed", e);
                    currentStatusMessage = "Failed: " + e.getMessage();
                    // Try to clean up temp file
                    try { Files.deleteIfExists(tempFile); } catch (Exception ignored) {}
                } finally {
                    isBackingUp = false;
                }
            });

            return "{\"status\": \"success\", \"message\": \"Backup started in background\", \"filename\": \"" + fileName + "\"}";
        } catch (Exception e) {
            isBackingUp = false;
            AdminWebDashPlugin.getCustomLogger().log("ERROR", "Backup failed to start", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private static void cleanupOldBackups() {
        try {
            List<Path> backups = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupDirectory, "*.zip")) {
                for (Path entry : stream) {
                    backups.add(entry);
                }
            }
            
            if (backups.size() > MAX_BACKUPS) {
                // Sort by date (oldest first)
                backups.sort((p1, p2) -> {
                    try {
                        return Files.getLastModifiedTime(p1).compareTo(Files.getLastModifiedTime(p2));
                    } catch (IOException e) {
                        return 0;
                    }
                });
                
                int toDelete = backups.size() - MAX_BACKUPS;
                for (int i = 0; i < toDelete; i++) {
                    Path p = backups.get(i);
                    AdminWebDashPlugin.getCustomLogger().info("Retention Policy: Deleting old backup " + p.getFileName());
                    Files.deleteIfExists(p);
                }
            }
        } catch (Exception e) {
             AdminWebDashPlugin.getCustomLogger().log("ERROR", "Failed to cleanup old backups", e);
        }
    }

    private static int countFiles(Path sourceDir) {
        final java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger(0);
        try {
            Files.walkFileTree(sourceDir, new java.nio.file.SimpleFileVisitor<Path>() {
                @Override
                public java.nio.file.FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                    if (dir.endsWith("logs") || dir.endsWith("Backups")) {
                        return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
                @Override
                public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                    count.incrementAndGet();
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            AdminWebDashPlugin.getCustomLogger().log("ERROR", "Failed to count files", e);
        }
        return count.get();
    }

    private static void zipDirectory(Path sourceDir, Path zipFile) throws IOException {
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(zipFile))) {
            Files.walkFileTree(sourceDir, new java.nio.file.SimpleFileVisitor<Path>() {
                @Override
                public java.nio.file.FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                    // Exclude logs directory as it changes frequently causing CRC errors
                    if (dir.endsWith("logs") || dir.endsWith("Backups")) {
                        return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                    try {
                        // Skip if file is inside logs or backups (double check)
                        // Also skip the backup file itself if sourceDir is parent of backupDir
                        if (file.equals(zipFile)) return java.nio.file.FileVisitResult.CONTINUE;
                        
                        Path targetFile = sourceDir.relativize(file);
                        
                        zos.putNextEntry(new ZipEntry(targetFile.toString()));
                        
                        // Read file and write to zip
                        // Handle file read errors gracefully (e.g. file locked/changed)
                        try (InputStream is = Files.newInputStream(file)) {
                             byte[] buffer = new byte[1024];
                             int len;
                             while ((len = is.read(buffer)) > 0) {
                                 zos.write(buffer, 0, len);
                             }
                        } catch (IOException e) {
                             System.err.println("Failed to zip file (skipping): " + file + " - " + e.getMessage());
                        } finally {
                            zos.closeEntry();
                            processedFiles.incrementAndGet();
                        }

                    } catch (IOException e) {
                         // Similar handling for ZipEntry errors
                         System.err.println("Failed to add zip entry: " + file);
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
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
                    
                    String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new java.util.Date());
                    String fileName = "scheduled_" + timestamp + ".zip";
                    Path backupFile = backupDirectory.resolve(fileName);
                    Path sourceDir = Universe.get().getPath();

                    zipDirectory(sourceDir, backupFile);
                    AdminWebDashPlugin.getCustomLogger().info("Scheduled backup completed: " + fileName);
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
            
            // Update and save config
            AdminWebDashPlugin.getInstance().updateBackupInterval(interval);
            
            // scheduling happens via the plugin reloading or we can just update the task here?
            // The plugin loads config on start. But we also need to update the running task.
            // setSchedule is called by the plugin start, but we should also call it here for immediate effect.
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
