package uk.co.grimtech.admin.web;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import uk.co.grimtech.admin.AdminWebDashPlugin;
import uk.co.grimtech.admin.CustomLogger;

public class AvatarCache {
  private static CustomLogger LOGGER;

  private static CustomLogger getLogger() {
    if (LOGGER == null) {
      LOGGER = AdminWebDashPlugin.getCustomLogger();
    }
    return LOGGER;
  }

  private static final String CACHE_DIR = "web/avatars/";
  private static final String BASE_URL = "https://hytaletracker.org/avatars/";

  static {
    try {
      Files.createDirectories(Paths.get(CACHE_DIR));
    } catch (IOException e) {
      getLogger().log("ERROR", "Could not create cache directory", e);
    }
  }

  public static byte[] getAvatar(String identifier) {
    String cacheKey = "avatar_" + identifier;

    byte[] cachedInMemory = ResourceCache.get(cacheKey);
    if (cachedInMemory != null) {
      return cachedInMemory;
    }

    String filename = identifier + ".png";
    Path cachePath = Paths.get(CACHE_DIR, filename);

    if (Files.exists(cachePath)) {
      try {
        byte[] data = Files.readAllBytes(cachePath);
        if (data != null) {
          ResourceCache.put(cacheKey, data, 3600000);
          return data;
        }
      } catch (IOException e) {
        getLogger().log("WARN", "Failed to read cached avatar: " + filename, e);
      }
    }

    try {
      byte[] data = downloadAvatar(identifier);
      if (data != null) {
        Files.write(cachePath, data);
        ResourceCache.put(cacheKey, data, 3600000);
        return data;
      }
    } catch (IOException e) {
      getLogger().log("WARN", "Failed to download and cache avatar: " + identifier, e);
    }

    return null;
  }

  private static byte[] downloadAvatar(String identifier) throws IOException {
    URL url = new URL(BASE_URL + identifier + ".png");
    getLogger().info("[Avatar] Downloading: " + url);
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
