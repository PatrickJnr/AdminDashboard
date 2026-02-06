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
    
    public CustomLogger(String logFilePath) {
        this.logFilePath = logFilePath;
    }
    
    public void info(String message) {
        if (AdminDashboardPlugin.isLoggingEnabled()) {
            log("INFO", message);
        }
    }
    
    public void warning(String message) {
        if (AdminDashboardPlugin.isLoggingEnabled()) {
            log("WARN", message);
        }
    }
    
    public void severe(String message) {
        if (AdminDashboardPlugin.isLoggingEnabled()) {
            log("ERROR", message);
        }
    }
    
    public void log(String level, String message) {
        if (!AdminDashboardPlugin.isLoggingEnabled()) {
            return;
        }
        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(logFilePath, true)))) {
            String timestamp = LocalDateTime.now().format(formatter);
            writer.println(String.format("[%s %5s] %s", timestamp, level, message));
        } catch (IOException e) {
            System.err.println("[AdminDashboard] Failed to write to log: " + e.getMessage());
        }
    }
    
    public void log(String level, String message, Throwable throwable) {
        if (!AdminDashboardPlugin.isLoggingEnabled()) {
            return;
        }
        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(logFilePath, true)))) {
            String timestamp = LocalDateTime.now().format(formatter);
            writer.println(String.format("[%s %5s] %s", timestamp, level, message));
            throwable.printStackTrace(writer);
        } catch (IOException e) {
            System.err.println("[AdminDashboard] Failed to write to log: " + e.getMessage());
        }
    }
}
