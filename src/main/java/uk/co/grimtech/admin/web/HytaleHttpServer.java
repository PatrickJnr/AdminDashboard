package uk.co.grimtech.admin.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import uk.co.grimtech.admin.web.DashboardAPI;
import uk.co.grimtech.admin.AdminWebDashPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class HytaleHttpServer {
    private final int port;
    private HttpServer server;

    public HytaleHttpServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // Static assets handler
        StaticHandler staticHandler = new StaticHandler();
        staticHandler.preLoadAssets();
        server.createContext("/", staticHandler);
        
        // API handler
        server.createContext("/api", new ApiHandler());

        server.setExecutor(null); // creates a default executor
        server.start();
    }
    
    public int getActualPort() {
        return server != null ? server.getAddress().getPort() : port;
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    static class StaticHandler implements HttpHandler {
        private final java.util.Map<String, byte[]> assetCache = new java.util.HashMap<>();
        private long lastPreloadTime = 0;

        public void preLoadAssets() {
            String[] assets = {
                "/web/index.html",
                "/web/css/styles.css",
                "/web/js/dashboard.js",
                "/web/js/charts.js",
                "/web/assets/img/logo.png",
                "/web/assets/fonts/Hytale.woff2"
                // Add more as needed, but let's stick to core ones or scan
            };
            
            for (String asset : assets) {
                byte[] data = loadFromResources(asset);
                if (data != null) {
                    assetCache.put(asset, data);
                }
            }
            lastPreloadTime = System.currentTimeMillis();
            AdminWebDashPlugin.getCustomLogger().info("[HTTP] Pre-loaded " + assetCache.size() + " static assets.");
        }

        @Override
        public void handle(HttpExchange t) throws IOException {
            String origPath = t.getRequestURI().getPath();
            String path = origPath;
            
            // Clean URL Routing: Serve index.html for known routes
            if (path.equals("/") || 
                path.equals("/dashboard") || 
                path.equals("/server") || 
                path.equals("/players") || 
                path.equals("/moderation") || 
                path.equals("/world") || 
                path.equals("/metrics") || 
                path.equals("/logs") || 
                path.equals("/config") || 
                path.equals("/files") || 
                path.equals("/info")) {
                
                path = "/index.html";
            }
            
            // Legacy redirect (optional, but good for compatibility)
            if (path.equals("/dashboard.html")) {
                path = "/index.html";
            }
            
            String resourcePath = "/web" + path;
            byte[] response = assetCache.get(resourcePath);
            
            if (response == null) {
                // Fallback to direct load or 404
                response = loadFromResources(resourcePath);
            }

            if (response == null) {
                String error = "404 Not Found";
                t.sendResponseHeaders(404, error.length());
                try (OutputStream os = t.getResponseBody()) {
                    os.write(error.getBytes());
                }
                return;
            }

            // Content-Type detection
            String contentType = "text/plain";
            if (path.endsWith(".html")) contentType = "text/html; charset=utf-8";
            else if (path.endsWith(".css")) contentType = "text/css";
            else if (path.endsWith(".js")) contentType = "application/javascript";
            else if (path.endsWith(".png")) contentType = "image/png";
            else if (path.endsWith(".jpg") || path.endsWith(".jpeg")) contentType = "image/jpeg";
            else if (path.endsWith(".svg")) contentType = "image/svg+xml";
            else if (path.endsWith(".woff2")) contentType = "font/woff2";

            t.getResponseHeaders().set("Content-Type", contentType);

            // Caching headers - Use a static ETag for pre-loaded assets to allow browser caching
            // but also allow cache-busting via our versioning if needed
            t.getResponseHeaders().set("Cache-Control", "public, max-age=3600"); // 1 hour
            t.getResponseHeaders().set("ETag", "\"" + lastPreloadTime + "_" + resourcePath.hashCode() + "\"");

            // If it's the HTML file, inject a version parameter into the content
            if (path.endsWith(".html")) {
                String html = new String(response, java.nio.charset.StandardCharsets.UTF_8);
                // Inject version once or on every hit? For now, we keep it dynamic if we want live updates
                // but we could also pre-process this.
                String version = String.valueOf(lastPreloadTime);
                html = html.replace("<script>", "<script>\n        const APP_VERSION = '" + version + "';\n        ");
                response = html.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }

            t.sendResponseHeaders(200, response.length);
            try (OutputStream os = t.getResponseBody()) {
                os.write(response);
            }
        }

        private byte[] loadFromResources(String path) {
            try (InputStream is = getClass().getResourceAsStream(path)) {
                if (is == null) return null;
                return is.readAllBytes();
            } catch (IOException e) {
                return null;
            }
        }
    }

    static class ApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String path = t.getRequestURI().getPath();
            String method = t.getRequestMethod();
            
            // Log EVERY request that hits the API handler
            AdminWebDashPlugin.getCustomLogger().info("[HTTP] Incoming: " + method + " " + path);

            // Set CORS headers early
            t.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            t.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            t.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-Admin-Token");

            // Handle preflight requests
            if ("OPTIONS".equalsIgnoreCase(method)) {
                t.sendResponseHeaders(204, -1);
                t.close();
                return;
            }

            // Token Validation (skip for public endpoints like item icons, avatars, and version)
            // Note: itemId might be encoded, so check start/end carefully
            boolean isPublicEndpoint = (path.startsWith("/api/item/") || path.startsWith("/api/mod/")) && path.endsWith("/icon") 
                                        || path.equals("/api/version")
                                        || path.startsWith("/api/avatar/");
            
            if (!isPublicEndpoint) {
                String authToken = t.getRequestHeaders().getFirst("X-Admin-Token");
                String expectedToken = AdminWebDashPlugin.getAdminToken();
                if (expectedToken != null && !expectedToken.equals(authToken)) {
                    AdminWebDashPlugin.getCustomLogger().warning("[HTTP] 401 Unauthorized for path: " + path);
                    String error = "{\"error\": \"Unauthorized - Invalid Token\"}";
                    t.getResponseHeaders().set("Content-Type", "application/json");
                    t.sendResponseHeaders(401, error.length());
                    try (OutputStream os = t.getResponseBody()) {
                        os.write(error.getBytes(StandardCharsets.UTF_8));
                    }
                    t.close();
                    return;
                }
            }

            String body = "";
            if ("POST".equalsIgnoreCase(method)) {
                try (Scanner scanner = new Scanner(t.getRequestBody(), StandardCharsets.UTF_8)) {
                    if (scanner.hasNext()) {
                        body = scanner.useDelimiter("\\A").next();
                    }
                } catch (Exception e) {
                    body = "";
                }
            }

            String response;
            int statusCode = 200;
            try {
                response = DashboardAPI.handleRequest(path, method, body);
            } catch (Exception e) {
                response = "{\"error\": \"Internal Server Error: " + e.getMessage() + "\"}";
                statusCode = 500;
                e.printStackTrace();
            }
            
            String contentType = "application/json";
            byte[] responseBytes;
            
            if (response != null && response.startsWith("AVATAR_DATA:")) {
                responseBytes = java.util.Base64.getDecoder().decode(response.substring(12));
                contentType = "image/png";
            } else if (response != null && response.startsWith("IMAGE_DATA:")) {
                responseBytes = java.util.Base64.getDecoder().decode(response.substring(11));
                contentType = "image/png";
            } else {
                responseBytes = response != null ? response.getBytes(StandardCharsets.UTF_8) : new byte[0];
            }

            t.getResponseHeaders().set("Content-Type", contentType);
            t.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream os = t.getResponseBody()) {
                os.write(responseBytes);
            }
            t.close();
        }
    }
}
