package uk.co.grimtech.admin.web;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AvatarCache {
    private static final Logger LOGGER = Logger.getLogger("AvatarCache");
    private static final String CACHE_DIR = "web/avatars/";
    private static final String BASE_URL = "https://hytaletracker.org/avatars/";

    static {
        try {
            Files.createDirectories(Paths.get(CACHE_DIR));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not create cache directory", e);
        }
    }

    public static byte[] getAvatar(String identifier) {
        String filename = identifier + ".png";
        Path cachePath = Paths.get(CACHE_DIR, filename);

        if (Files.exists(cachePath)) {
            try {
                return Files.readAllBytes(cachePath);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to read cached avatar: " + filename, e);
            }
        }

        // Not in cache, download it
        try {
            byte[] data = downloadAvatar(identifier);
            if (data != null) {
                Files.write(cachePath, data);
                return data;
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to download and cache avatar: " + identifier, e);
        }

        return null;
    }

    private static byte[] downloadAvatar(String identifier) throws IOException {
        URL url = new URL(BASE_URL + identifier + ".png");
        LOGGER.info("[Avatar] Downloading: " + url);
        try (InputStream in = new BufferedInputStream(url.openStream());
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }
}
