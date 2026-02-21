package uk.co.grimtech.admin.util;

import uk.co.grimtech.admin.AdminWebDashPlugin;
import uk.co.grimtech.admin.CustomLogger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.List;

public class AuthManager {
    // Maps Session ID to User IP Address
    private static final Map<String, String> activeSessions = new ConcurrentHashMap<>();
    
    // Maps IP to integer attempts
    private static final Map<String, LoginAttempt> loginAttempts = new ConcurrentHashMap<>();
    
    // 5 minutes lockout duration
    private static final long LOCKOUT_DURATION_MS = 5 * 60 * 1000L;
    
    private static class LoginAttempt {
        int count = 0;
        long lastAttemptTime = System.currentTimeMillis();
    }

    private static CustomLogger getLogger() {
        return AdminWebDashPlugin.getCustomLogger();
    }

    public static boolean isIpAllowed(String ip) {
        List<String> allowlist = AdminWebDashPlugin.getIpAllowlist();
        if (allowlist == null || allowlist.isEmpty()) {
            return true; // If empty, allow all
        }
        return allowlist.contains(ip);
    }

    public static boolean canAttemptLogin(String ip) {
        LoginAttempt attempt = loginAttempts.get(ip);
        if (attempt == null) return true;
        
        if (attempt.count >= AdminWebDashPlugin.getLoginRateLimit()) {
            if (System.currentTimeMillis() - attempt.lastAttemptTime > LOCKOUT_DURATION_MS) {
                // Lockout expired
                loginAttempts.remove(ip);
                return true;
            }
            return false;
        }
        return true;
    }

    public static void recordFailedLogin(String ip) {
        loginAttempts.compute(ip, (key, attempt) -> {
            if (attempt == null) {
                attempt = new LoginAttempt();
            }
            attempt.count++;
            attempt.lastAttemptTime = System.currentTimeMillis();
            return attempt;
        });
        getLogger().warning("[Auth] Failed login attempt from IP: " + ip);
    }

    public static void recordSuccessfulLogin(String ip) {
        loginAttempts.remove(ip);
    }

    public static String createSession(String ip) {
        String sessionId = UUID.randomUUID().toString();
        activeSessions.put(sessionId, ip);
        getLogger().info("[Auth] New session created for IP: " + ip);
        return sessionId;
    }

    public static boolean isValidSession(String sessionId, String ip) {
        if (sessionId == null || sessionId.isEmpty()) return false;
        // Basic check, does the session exist.
        // As a strict security measure, we could verify IP matches:
        // String boundIp = activeSessions.get(sessionId);
        // return ip.equals(boundIp);
        // For now, simpler validation:
        return activeSessions.containsKey(sessionId);
    }
    
    public static void invalidateSession(String sessionId) {
        if (sessionId != null) {
            activeSessions.remove(sessionId);
        }
    }
}
