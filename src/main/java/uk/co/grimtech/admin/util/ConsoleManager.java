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
        return getLog(null);
    }

    public static String getChatLog() {
        return getLog("] [Hytale]");
    }

    private static String getLog(String filter) {
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
            
            File latestLog = null;
            for (File file : logFiles) {
                String name = file.getName();
                if (name.equals("dashboard.log")) continue;
                if (name.contains("server") || name.equals("latest.log")) {
                    latestLog = file;
                    break;
                }
            }

            if (latestLog == null && logFiles.length > 0) {
                 for (File file : logFiles) {
                    if (!file.getName().equals("dashboard.log")) {
                        latestLog = file;
                        break;
                    }
                }
            }
            
            if (latestLog == null) return error("No server logs found");

            return readLastLines(latestLog, MAX_LINES, filter);

        } catch (Exception e) {
            AdminWebDashPlugin.getCustomLogger().log("ERROR", "Failed to read logs", e);
            return error("Internal error reading logs");
        }
    }

    private static String readLastLines(File file, int linesToRead, String filter) {
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
                    if (pointer < fileLength - 1) {
                        String line = sb.reverse().toString();
                        line = line.replace("\r", "");
                        
                        if (filter == null || line.contains(filter)) {
                            lines.add(0, line);
                            linesRead++;
                        }
                        
                        sb.setLength(0);
                    }
                } else if (b != '\r') {
                    sb.append((char) b);
                }
                pointer--;
            }
            
            if (sb.length() > 0 && linesRead < linesToRead) {
                String line = sb.reverse().toString();
                line = line.replace("\r", "");
                if (filter == null || line.contains(filter)) {
                    lines.add(0, line);
                }
            }
        } catch (IOException e) {
            AdminWebDashPlugin.getCustomLogger().log("ERROR", "Error reading log file", e);
        }

        JsonObject response = new JsonObject();
        response.addProperty("status", "success");
        response.addProperty("filename", file.getName());
        
        com.google.gson.JsonArray logsArray = new com.google.gson.JsonArray();
        for (String line : lines) {
            logsArray.add(line);
        }
        response.add("logs", logsArray);
        
        return GSON.toJson(response);
    }

    private static String error(String message) {
        JsonObject json = new JsonObject();
        json.addProperty("error", message);
        return GSON.toJson(json);
    }
}
