package uk.co.grimtech.admin.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import uk.co.grimtech.admin.web.DashboardAPI;

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
        server.createContext("/", new StaticHandler());
        
        // API handler
        server.createContext("/api", new ApiHandler());

        server.setExecutor(null); // creates a default executor
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    static class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String path = t.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";
            
            // For now, let's serve a simple embedded HTML if the file isn't found
            // In a real scenario, we'd load from resources
            byte[] response = loadFromResources("/web" + path);
            if (response == null) {
                String error = "404 Not Found";
                t.sendResponseHeaders(404, error.length());
                OutputStream os = t.getResponseBody();
                os.write(error.getBytes());
                os.close();
                return;
            }

            t.sendResponseHeaders(200, response.length);
            OutputStream os = t.getResponseBody();
            os.write(response);
            os.close();
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
            
            // Set CORS headers early
            t.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            t.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            t.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

            // Handle preflight requests
            if ("OPTIONS".equalsIgnoreCase(method)) {
                t.sendResponseHeaders(204, -1);
                t.close();
                return;
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
