package uk.co.grimtech.admin.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import uk.co.grimtech.admin.AdminWebDashPlugin;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class ConsoleManager {
    private static final Gson GSON = new Gson();
    private static final int MAX_LINES = 200;

    public static String getConsoleLog() {
        try {

            File logsDir = new File("logs");
            if (!logsDir.exists() || !logsDir.isDirectory()) {
                return error("Logs directory not found");
            }


            File[] logFiles = logsDir.listFiles((dir, name) -> name.endsWith(".log"));
            
            if (logFiles == null || logFiles.length == 0) {
                return error("No log files found");
            }


            Arrays.sort(logFiles, Comparator.comparingLong(File::lastModified).reversed());
            
            // Pick the newest file that IS NOT the dashboard log
            File latestLog = null;
            for (File file : logFiles) {
                String name = file.getName();
                if (name.equals("dashboard.log")) continue;
                // Prefer server logs
                if (name.contains("server") || name.equals("latest.log")) {
                    latestLog = file;
                    break;
                }
            }
            // Fallback to absolute newest if no server log found (but still skipping dashboard.log)
            if (latestLog == null && logFiles.length > 0) {
                 for (File file : logFiles) {
                    if (!file.getName().equals("dashboard.log")) {
                        latestLog = file;
                        break;
                    }
                }
            }
            
            if (latestLog == null) return error("No server logs found");

            return readLastLines(latestLog, MAX_LINES);

        } catch (Exception e) {
            AdminWebDashPlugin.getCustomLogger().log("ERROR", "Failed to read console log", e);
            return error("Internal error reading logs");
        }
    }

    private static String readLastLines(File file, int linesToRead) {
        List<String> lines = new ArrayList<>();
        

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long fileLength = raf.length();
            long pointer = fileLength - 1;
            int linesRead = 0;
            StringBuilder sb = new StringBuilder();

            // Read backwards
            while (pointer >= 0 && linesRead < linesToRead) {
                raf.seek(pointer);
                int b = raf.read();

                if (b == '\n') {
                    // Line break found
                    if (pointer < fileLength - 1) { // Skip trailing newline
                        String line = sb.reverse().toString();
                        // Sanitize line for JSON
                        line = line.replace("\r", "");
                        lines.add(0, line);
                        sb.setLength(0);
                        linesRead++;
                    }
                } else if (b != '\r') {
                    sb.append((char) b);
                }
                pointer--;
            }
            
            // Add the last remaining line (first line of file)
            if (sb.length() > 0) {
                String line = sb.reverse().toString();
                line = line.replace("\r", "");
                lines.add(0, line);
            }
        } catch (IOException e) {
            AdminWebDashPlugin.getCustomLogger().log("ERROR", "Error reading log file", e);
            // Return what we have so far instead of failing completely
        }

        JsonObject response = new JsonObject();
        response.addProperty("status", "success");
        response.addProperty("filename", file.getName());
        
        com.google.gson.JsonArray linesArray = new com.google.gson.JsonArray();
        for (String line : lines) {
            linesArray.add(line);
        }
        response.add("lines", linesArray);
        
        return GSON.toJson(response);
    }

    private static String error(String message) {
        JsonObject json = new JsonObject();
        json.addProperty("error", message);
        return GSON.toJson(json);
    }
}
