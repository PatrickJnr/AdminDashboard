package uk.co.grimtech.admin.web;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * A simple in-memory cache utility for expensive resources.
 */
public class ResourceCache {
    private static final Map<String, CachedResource> cache = new ConcurrentHashMap<>();

    private static class CachedResource {
        final Object data;
        final long expiry;

        CachedResource(Object data, long ttlMillis) {
            this.data = data;
            this.expiry = ttlMillis > 0 ? System.currentTimeMillis() + ttlMillis : Long.MAX_VALUE;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiry;
        }
    }

    /**
     * Store a resource in the cache.
     * @param key Unique key for the resource.
     * @param data The data to store.
     * @param ttlMillis Time to live in milliseconds (0 for infinite).
     */
    public static void put(String key, Object data, long ttlMillis) {
        cache.put(key, new CachedResource(data, ttlMillis));
    }

    /**
     * Retrieve a resource from the cache.
     * @param key Unique key for the resource.
     * @return The data if present and not expired, null otherwise.
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(String key) {
        CachedResource cached = cache.get(key);
        if (cached == null) return null;
        if (cached.isExpired()) {
            cache.remove(key);
            return null;
        }
        return (T) cached.data;
    }

    /**
     * Clear specific entries or the whole cache.
     */
    public static void clear() {
        cache.clear();
    }

    public static void remove(String key) {
        cache.remove(key);
    }
}
