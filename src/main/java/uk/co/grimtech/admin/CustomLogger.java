package uk.co.grimtech.admin;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Custom logger that writes directly to a file, bypassing Java's Logger system
 * which is being intercepted by Hytale's logging infrastructure.
 */
public class CustomLogger {
    private final String logFilePath;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    private final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    
    public CustomLogger(String logFilePath) {
        this.logFilePath = logFilePath;
    }
    
    public void debug(String message) {
        if (AdminWebDashPlugin.isLoggingEnabled() && "DEBUG".equalsIgnoreCase(AdminWebDashPlugin.getLogLevel())) {
            log("DEBUG", message);
        }
    }

    public void info(String message) {
        if (AdminWebDashPlugin.isLoggingEnabled() && shouldLog("INFO")) {
            log("INFO", message);
        }
    }
    
    public void warning(String message) {
        if (AdminWebDashPlugin.isLoggingEnabled() && shouldLog("WARN")) {
            log("WARN", message);
        }
    }
    
    public void severe(String message) {
        if (AdminWebDashPlugin.isLoggingEnabled() && shouldLog("ERROR")) {
            log("ERROR", message);
        }
    }

    private boolean shouldLog(String level) {
        String configLevel = AdminWebDashPlugin.getLogLevel() != null ? AdminWebDashPlugin.getLogLevel().toUpperCase() : "INFO";
        if (configLevel.equals("DEBUG")) return true;
        if (configLevel.equals("INFO") && !level.equals("DEBUG")) return true;
        if (configLevel.equals("WARN") && (level.equals("WARN") || level.equals("ERROR"))) return true;
        if (configLevel.equals("ERROR") && level.equals("ERROR")) return true;
        return false;
    }

    private void checkRotation() {
        java.io.File f = new java.io.File(logFilePath);
        if (f.exists() && f.length() > MAX_FILE_SIZE) {
            java.io.File oldFile = new java.io.File(logFilePath.replace(".log", "-old.log"));
            if (oldFile.exists()) {
                oldFile.delete();
            }
            f.renameTo(oldFile);
        }
    }
    
    public void log(String level, String message) {
        if (!AdminWebDashPlugin.isLoggingEnabled()) {
            return;
        }
        checkRotation();
        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(logFilePath, true)))) {
            String timestamp = LocalDateTime.now().format(formatter);
            writer.println(String.format("[%s %5s] %s", timestamp, level, message));
        } catch (IOException e) {
            System.err.println("[AdminWebDash] Failed to write to log: " + e.getMessage());
        }
    }
    
    public void log(String level, String message, Throwable throwable) {
        if (!AdminWebDashPlugin.isLoggingEnabled()) {
            return;
        }
        checkRotation();
        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(logFilePath, true)))) {
            String timestamp = LocalDateTime.now().format(formatter);
            writer.println(String.format("[%s %5s] %s", timestamp, level, message));
            throwable.printStackTrace(writer);
        } catch (IOException e) {
            System.err.println("[AdminWebDash] Failed to write to log: " + e.getMessage());
        }
    }
}
