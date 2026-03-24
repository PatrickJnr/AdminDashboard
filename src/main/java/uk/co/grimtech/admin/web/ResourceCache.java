package uk.co.grimtech.admin.web;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;


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

    
    public static void put(String key, Object data, long ttlMillis) {
        cache.put(key, new CachedResource(data, ttlMillis));
    }

    
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

    
    public static void clear() {
        cache.clear();
    }

    public static void remove(String key) {
        cache.remove(key);
    }
}
