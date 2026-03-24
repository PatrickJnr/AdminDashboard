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
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

public class HytaleHttpServer {
    private final int port;
    private HttpServer server;

    public HytaleHttpServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        int actualPort = port;
        InetSocketAddress address = AdminWebDashPlugin.isReverseProxy() 
                ? new InetSocketAddress("127.0.0.1", actualPort) 
                : new InetSocketAddress(actualPort);
        
        
        if (AdminWebDashPlugin.useHttps()) {
            try {
                server = HttpsServer.create(address, 0);
                SSLContext sslContext = createSSLContext();
                ((HttpsServer) server).setHttpsConfigurator(new HttpsConfigurator(sslContext) {
                    public void configure(HttpsParameters params) {
                        try {
                            SSLContext c = getSSLContext();
                            params.setNeedClientAuth(false);
                            params.setCipherSuites(c.createSSLEngine().getSupportedCipherSuites());
                            params.setProtocols(c.createSSLEngine().getSupportedProtocols());
                        } catch (Exception ex) {
                            System.err.println("Failed to configure HTTPS parameters");
                        }
                    }
                });
                AdminWebDashPlugin.getCustomLogger().info("[HTTP] Starting HTTPS Server on port " + actualPort);
            } catch (Exception e) {
                AdminWebDashPlugin.getCustomLogger().severe("[HTTP] Failed to create HTTPS server, falling back to HTTP: " + e.getMessage());
                server = HttpServer.create(address, 0);
            }
        } else {
            server = HttpServer.create(address, 0);
            AdminWebDashPlugin.getCustomLogger().info("[HTTP] Starting HTTP Server on port " + actualPort + (AdminWebDashPlugin.isReverseProxy() ? " (Reverse Proxy Mode - Bound to 127.0.0.1)" : ""));
        }
        
        StaticHandler staticHandler = new StaticHandler();
        staticHandler.preLoadAssets();
        server.createContext("/", staticHandler);
        
        server.createContext("/api", new ApiHandler());

        server.setExecutor(null); 
        server.start();
    }
    
    private SSLContext createSSLContext() throws Exception {
        String keystorePath = AdminWebDashPlugin.getKeystorePath();
        String keystorePassword = AdminWebDashPlugin.getKeystorePassword();
        
        File keystoreFile = new File(keystorePath);
        if (!keystoreFile.isAbsolute()) {
            keystoreFile = new File("mods/AdminWebDash", keystorePath);
        }
        
        if (!keystoreFile.exists()) {
            boolean certGenerated = false;
            if (AdminWebDashPlugin.isLetsEncrypt() && AdminWebDashPlugin.getDomain() != null && !AdminWebDashPlugin.getDomain().isEmpty()) {
                AdminWebDashPlugin.getCustomLogger().info("[HTTP] Attempting Let's Encrypt Certificate Generation for " + AdminWebDashPlugin.getDomain());
                certGenerated = uk.co.grimtech.admin.util.LetsEncryptManager.checkAndRenewCertificate(AdminWebDashPlugin.getDomain(), AdminWebDashPlugin.getLetsEncryptEmail(), keystoreFile, keystorePassword);
            }
            if (!certGenerated) {
                AdminWebDashPlugin.getCustomLogger().info("[HTTP] Keystore not found at " + keystoreFile.getAbsolutePath() + " - generating a self-signed cert...");
                generateSelfSignedCert(keystoreFile, keystorePassword);
            }
        }

        char[] password = keystorePassword.toCharArray();
        KeyStore ks = KeyStore.getInstance("JKS");
        FileInputStream fis = new FileInputStream(keystoreFile);
        ks.load(fis, password);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, password);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ks);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return sslContext;
    }

    private void generateSelfSignedCert(File keystoreFile, String password) throws Exception {
        
        String javaHome = System.getProperty("java.home");
        File keytool = new File(javaHome, "bin/keytool");
        if (!keytool.exists()) {
            keytool = new File(javaHome, "bin/keytool.exe");
        }
        
        if (!keytool.exists()) {
            throw new Exception("Could not find keytool at " + keytool.getAbsolutePath());
        }
        
        String domain = AdminWebDashPlugin.getDomain();
        if (domain == null || domain.isEmpty()) {
            domain = "localhost";
        }

        
        File parentDir = keystoreFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        
        ProcessBuilder pb = new ProcessBuilder(
            keytool.getAbsolutePath(),
            "-genkeypair",
            "-alias", "adminwebdash",
            "-keyalg", "RSA",
            "-keysize", "2048",
            "-storetype", "JKS",
            "-keystore", keystoreFile.getAbsolutePath(),
            "-validity", "3650",
            "-storepass", password,
            "-keypass", password,
            "-dname", "CN=" + domain + ", OU=Hytale, O=Grimtech, L=City, ST=State, C=US"
        );
        
        pb.redirectErrorStream(true);
        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new Exception("keytool failed with exit code " + exitCode + ": " + output);
        }
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
            
            
            if (path.equals("/dashboard.html")) {
                path = "/index.html";
            }
            
            String resourcePath = "/web" + path;
            byte[] response = assetCache.get(resourcePath);
            
            if (response == null) {
                
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

            String contentType = "text/plain";
            if (path.endsWith(".html")) contentType = "text/html; charset=utf-8";
            else if (path.endsWith(".css")) contentType = "text/css";
            else if (path.endsWith(".js")) contentType = "application/javascript";
            else if (path.endsWith(".png")) contentType = "image/png";
            else if (path.endsWith(".jpg") || path.endsWith(".jpeg")) contentType = "image/jpeg";
            else if (path.endsWith(".svg")) contentType = "image/svg+xml";
            else if (path.endsWith(".woff2")) contentType = "font/woff2";

            t.getResponseHeaders().set("Content-Type", contentType);

            
            
            t.getResponseHeaders().set("Cache-Control", "public, max-age=3600"); 
            t.getResponseHeaders().set("ETag", "\"" + lastPreloadTime + "_" + resourcePath.hashCode() + "\"");

            
            if (path.endsWith(".html")) {
                String html = new String(response, java.nio.charset.StandardCharsets.UTF_8);
                
                
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

            
            if (path.startsWith("/.well-known/acme-challenge/")) {
                String token = path.substring(path.lastIndexOf('/') + 1);
                String challengeContent = uk.co.grimtech.admin.util.LetsEncryptManager.getChallengeContent(token);
                if (challengeContent != null) {
                    t.sendResponseHeaders(200, challengeContent.length());
                    try (OutputStream os = t.getResponseBody()) {
                        os.write(challengeContent.getBytes(StandardCharsets.UTF_8));
                    }
                    return;
                }
            }

            AdminWebDashPlugin.getCustomLogger().debug("[HTTP] Incoming: " + method + " " + path);

            
            t.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            t.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            t.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-Admin-Token");

            if ("OPTIONS".equalsIgnoreCase(method)) {
                t.sendResponseHeaders(204, -1);
                t.close();
                return;
            }

            String clientIp = t.getRemoteAddress().getAddress().getHostAddress();
            if (AdminWebDashPlugin.isReverseProxy()) {
                String forwardedFor = t.getRequestHeaders().getFirst("X-Forwarded-For");
                if (forwardedFor != null && !forwardedFor.isEmpty()) {
                    clientIp = forwardedFor.split(",")[0].trim();
                }
            }

            
            if (!uk.co.grimtech.admin.util.AuthManager.isIpAllowed(clientIp)) {
                sendErrorResponse(t, 403, "Forbidden - IP Not Allowed");
                return;
            }

            
            boolean isPublicEndpoint = (path.startsWith("/api/item/") || path.startsWith("/api/mod/")) && path.endsWith("/icon") 
                                        || path.equals("/api/version")
                                        || path.startsWith("/api/avatar/");
            
            String sessionId = getCookie(t, "session_id");

            if (path.equals("/api/login") && "POST".equalsIgnoreCase(method)) {
                handleLogin(t, clientIp);
                return;
            }
            if (path.equals("/api/logout") && "POST".equalsIgnoreCase(method)) {
                handleLogout(t, sessionId);
                return;
            }

            if (!isPublicEndpoint) {
                
                String authToken = t.getRequestHeaders().getFirst("X-Admin-Token");
                String expectedToken = AdminWebDashPlugin.getAdminToken();
                
                boolean isValidSession = uk.co.grimtech.admin.util.AuthManager.isValidSession(sessionId, clientIp);
                boolean isValidToken = expectedToken != null && expectedToken.equals(authToken);

                if (!isValidSession && !isValidToken) {
                    AdminWebDashPlugin.getCustomLogger().warning("[HTTP] 401 Unauthorized for path: " + path + " from IP: " + clientIp);
                    sendErrorResponse(t, 401, "Unauthorized - Invalid Session or Token");
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

        private String getCookie(HttpExchange t, String name) {
            java.util.List<String> cookies = t.getRequestHeaders().get("Cookie");
            if (cookies != null) {
                for (String cookieHeader : cookies) {
                    String[] parts = cookieHeader.split(";");
                    for (String part : parts) {
                        part = part.trim();
                        if (part.startsWith(name + "=")) {
                            return part.substring(name.length() + 1);
                        }
                    }
                }
            }
            return null;
        }

        private void handleLogin(HttpExchange t, String ip) throws IOException {
            if (!uk.co.grimtech.admin.util.AuthManager.canAttemptLogin(ip)) {
                sendErrorResponse(t, 429, "Too Many Requests - Try again later");
                return;
            }

            String authToken = t.getRequestHeaders().getFirst("X-Admin-Token");
            String expectedToken = AdminWebDashPlugin.getAdminToken();

            if (expectedToken != null && expectedToken.equals(authToken)) {
                uk.co.grimtech.admin.util.AuthManager.recordSuccessfulLogin(ip);
                String session = uk.co.grimtech.admin.util.AuthManager.createSession(ip);
                
                String cookieStr = "session_id=" + session + "; Path=/; HttpOnly; SameSite=Strict";
                if (AdminWebDashPlugin.useHttps()) cookieStr += "; Secure";
                t.getResponseHeaders().add("Set-Cookie", cookieStr);
                
                String response = "{\"success\": true}";
                t.getResponseHeaders().set("Content-Type", "application/json");
                t.sendResponseHeaders(200, response.length());
                try (OutputStream os = t.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
            } else {
                uk.co.grimtech.admin.util.AuthManager.recordFailedLogin(ip);
                sendErrorResponse(t, 401, "Invalid Admin Token");
            }
            t.close();
        }

        private void handleLogout(HttpExchange t, String sessionId) throws IOException {
            uk.co.grimtech.admin.util.AuthManager.invalidateSession(sessionId);
            
            String cookieStr = "session_id=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict";
            if (AdminWebDashPlugin.useHttps()) cookieStr += "; Secure";
            t.getResponseHeaders().add("Set-Cookie", cookieStr);
            
            String response = "{\"success\": true}";
            t.getResponseHeaders().set("Content-Type", "application/json");
            t.sendResponseHeaders(200, response.length());
            try (OutputStream os = t.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
            t.close();
        }

        private void sendErrorResponse(HttpExchange t, int code, String msg) throws IOException {
            String error = "{\"error\": \"" + msg + "\"}";
            t.getResponseHeaders().set("Content-Type", "application/json");
            t.sendResponseHeaders(code, error.length());
            try (OutputStream os = t.getResponseBody()) {
                os.write(error.getBytes(StandardCharsets.UTF_8));
            }
            t.close();
        }
    }
}
