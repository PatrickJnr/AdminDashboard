package uk.co.grimtech.admin.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import uk.co.grimtech.admin.AdminWebDashPlugin;
import uk.co.grimtech.admin.CustomLogger;

public class MuteTracker {
  private static final Gson GSON =
      new com.google.gson.GsonBuilder()
          .registerTypeAdapter(
              Instant.class,
              new com.google.gson.TypeAdapter<Instant>() {
                @Override
                public void write(com.google.gson.stream.JsonWriter out, Instant value)
                    throws IOException {
                  if (value == null) {
                    out.nullValue();
                  } else {
                    out.value(value.toString());
                  }
                }

                @Override
                public Instant read(com.google.gson.stream.JsonReader in) throws IOException {
                  if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                    in.nextNull();
                    return null;
                  }
                  return Instant.parse(in.nextString());
                }
              })
          .create();
  private static final Path MUTES_FILE = Paths.get("mutes.json");
  private static final Map<UUID, Mute> mutes = new ConcurrentHashMap<>();

  private static CustomLogger getLogger() {
    return AdminWebDashPlugin.getCustomLogger();
  }

  public static class Mute {
    public UUID player;
    public UUID mutedBy;
    public Instant timestamp;
    public Long durationSeconds;
    public String reason;

    public boolean isExpired() {
      if (durationSeconds == null) return false;
      return Instant.now().isAfter(timestamp.plusSeconds(durationSeconds));
    }

    public long getRemainingSeconds() {
      if (durationSeconds == null) return -1;
      if (isExpired()) return 0;
      long elapsed = Instant.now().getEpochSecond() - timestamp.getEpochSecond();
      return durationSeconds - elapsed;
    }
  }

  public static void load() {
    if (Files.exists(MUTES_FILE)) {
      try {
        String json = Files.readString(MUTES_FILE);
        Map<UUID, Mute> loaded = GSON.fromJson(json, new TypeToken<Map<UUID, Mute>>() {}.getType());
        if (loaded != null) {
          mutes.putAll(loaded);

          mutes.entrySet().removeIf(e -> e.getValue().isExpired());
          save();
          getLogger().info("[MuteTracker] Loaded " + mutes.size() + " active mutes");
        }
      } catch (Exception e) {
        getLogger().log("ERROR", "[MuteTracker] Failed to load mutes", e);
      }
    }
  }

  public static void save() {
    try {
      Files.writeString(MUTES_FILE, GSON.toJson(mutes));
    } catch (Exception e) {
      getLogger().log("ERROR", "[MuteTracker] Failed to save mutes", e);
    }
  }

  public static void mute(UUID player, UUID mutedBy, Long durationSeconds, String reason) {
    Mute mute = new Mute();
    mute.player = player;
    mute.mutedBy = mutedBy;
    mute.timestamp = Instant.now();
    mute.durationSeconds = durationSeconds;
    mute.reason = reason;
    mutes.put(player, mute);
    save();
    getLogger()
        .info(
            "[MuteTracker] Muted player "
                + player
                + " for "
                + (durationSeconds == null ? "permanent" : durationSeconds + "s"));
  }

  public static void unmute(UUID player) {
    mutes.remove(player);
    save();
    getLogger().info("[MuteTracker] Unmuted player " + player);
  }

  public static boolean isMuted(UUID player) {
    Mute mute = mutes.get(player);
    if (mute == null) return false;
    if (mute.isExpired()) {
      unmute(player);
      return false;
    }
    return true;
  }

  public static Mute getMute(UUID player) {
    Mute mute = mutes.get(player);
    if (mute != null && mute.isExpired()) {
      unmute(player);
      return null;
    }
    return mute;
  }

  public static Map<UUID, Mute> getMutes() {

    mutes.entrySet().removeIf(e -> e.getValue().isExpired());
    return new HashMap<>(mutes);
  }
}
