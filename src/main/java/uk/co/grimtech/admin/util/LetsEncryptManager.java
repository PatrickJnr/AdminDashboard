package uk.co.grimtech.admin.util;

import org.shredzone.acme4j.Account;
import org.shredzone.acme4j.AccountBuilder;
import org.shredzone.acme4j.Authorization;
import org.shredzone.acme4j.Certificate;
import org.shredzone.acme4j.Order;
import org.shredzone.acme4j.Session;
import org.shredzone.acme4j.Status;


import org.shredzone.acme4j.challenge.Http01Challenge;
import org.shredzone.acme4j.util.CSRBuilder;
import org.shredzone.acme4j.util.KeyPairUtils;
import uk.co.grimtech.admin.AdminWebDashPlugin;
import uk.co.grimtech.admin.CustomLogger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LetsEncryptManager {
    private static final String LETS_ENCRYPT_URL = "acme://letsencrypt.org";
    // For testing without rate limits, could use staging: "acme://letsencrypt.org/staging"
    
    private static final Map<String, String> acmeChallenges = new HashMap<>();
    private static ScheduledExecutorService renewalExecutor;
    private static ServerSocket challengeServerSocket;

    public static String getChallengeContent(String token) {
        return acmeChallenges.get(token);
    }

    /**
     * Starts a lightweight HTTP server on port 80 solely to serve ACME HTTP-01 challenges.
     * Let's Encrypt always validates on port 80, regardless of what port your HTTPS server runs on.
     */
    public static void startChallengeServer() {
        if (challengeServerSocket != null && !challengeServerSocket.isClosed()) return;
        try {
            challengeServerSocket = new ServerSocket(80);
            Thread t = new Thread(() -> {
                while (!challengeServerSocket.isClosed()) {
                    try {
                        Socket client = challengeServerSocket.accept();
                        new Thread(() -> {
                            try {
                                byte[] buf = new byte[4096];
                                int len = client.getInputStream().read(buf);
                                if (len <= 0) { client.close(); return; }
                                String req = new String(buf, 0, len);
                                // Parse path from request line
                                String path = "";
                                if (req.startsWith("GET ")) {
                                    int end = req.indexOf(' ', 4);
                                    if (end > 4) path = req.substring(4, end);
                                }
                                if (path.startsWith("/.well-known/acme-challenge/")) {
                                    String token = path.substring(path.lastIndexOf('/') + 1);
                                    String content = acmeChallenges.get(token);
                                    if (content != null) {
                                        String resp = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: " + content.length() + "\r\nConnection: close\r\n\r\n" + content;
                                        OutputStream os = client.getOutputStream();
                                        os.write(resp.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                                        os.flush();
                                    } else {
                                        String resp = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
                                        client.getOutputStream().write(resp.getBytes());
                                    }
                                } else {
                                    String resp = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
                                    client.getOutputStream().write(resp.getBytes());
                                }
                                client.close();
                            } catch (Exception ignore) {}
                        }).start();
                    } catch (Exception e) {
                        if (!challengeServerSocket.isClosed()) {
                            getLogger().warning("[Let's Encrypt] Challenge server error: " + e.getMessage());
                        }
                    }
                }
            });
            t.setDaemon(true);
            t.setName("ACME-ChallengeServer");
            t.start();
            getLogger().info("[Let's Encrypt] ACME challenge HTTP server started on port 80.");
        } catch (Exception e) {
            getLogger().warning("[Let's Encrypt] Could not start ACME challenge server on port 80: " + e.getMessage() + ". Ensure port 80 is free and the process has permission to bind it.");
        }
    }

    public static void stopChallengeServer() {
        if (challengeServerSocket != null && !challengeServerSocket.isClosed()) {
            try { challengeServerSocket.close(); } catch (Exception ignore) {}
        }
    }

    private static CustomLogger getLogger() {
        return AdminWebDashPlugin.getCustomLogger();
    }

    public static boolean checkAndRenewCertificate(String domain, String email, File keystoreFile, String keystorePassword) {
        if (domain == null || domain.isEmpty() || domain.equals("localhost") || domain.equals("127.0.0.1")) {
            getLogger().warning("[Let's Encrypt] Invalid domain for Let's Encrypt: " + domain);
            return false;
        }
        if (email == null || email.isEmpty()) {
            getLogger().warning("[Let's Encrypt] Email is required for Let's Encrypt registration.");
            return false;
        }

        try {
            File dataDir = new File("mods/AdminWebDash/ssl");
            if (!dataDir.exists()) dataDir.mkdirs();

            File userKeyFile = new File(dataDir, "user.key");
            File domainKeyFile = new File(dataDir, "domain.key");

            KeyPair userKeyPair;
            if (userKeyFile.exists()) {
                try (FileReader fr = new FileReader(userKeyFile)) {
                    userKeyPair = KeyPairUtils.readKeyPair(fr);
                }
            } else {
                userKeyPair = KeyPairUtils.createKeyPair(2048);
                try (FileWriter fw = new FileWriter(userKeyFile)) {
                    KeyPairUtils.writeKeyPair(userKeyPair, fw);
                }
            }

            Session session = new Session(LETS_ENCRYPT_URL);
            Account account = new AccountBuilder()
                    .agreeToTermsOfService()
                    .useKeyPair(userKeyPair)
                    .addEmail(email)
                    .create(session);

            Order order = account.newOrder().domains(domain).create();

            for (Authorization auth : order.getAuthorizations()) {
                if (auth.getStatus() == Status.VALID) continue;

                Http01Challenge challenge = auth.findChallenge(Http01Challenge.class).orElse(null);
                if (challenge == null) {
                    getLogger().severe("[Let's Encrypt] HTTP-01 challenge not available format");
                    return false;
                }


                String token = challenge.getToken();
                String content = challenge.getAuthorization();
                acmeChallenges.put(token, content);
                getLogger().info("[Let's Encrypt] Registered ACME Challenge for " + domain + ". Ensure port 80 traffic routes to this server.");

                challenge.trigger();

                try {
                    Status challengeStatus = challenge.waitForStatus(
                            java.util.EnumSet.of(Status.VALID, Status.INVALID), java.time.Duration.ofSeconds(300));
                    if (challengeStatus == Status.INVALID) {
                        getLogger().severe("[Let's Encrypt] Challenge failed. Error: " + challenge.getError());
                        acmeChallenges.remove(token);
                        return false;
                    }
                    if (challengeStatus != Status.VALID) {
                        getLogger().severe("[Let's Encrypt] Challenge timed out.");
                        acmeChallenges.remove(token);
                        return false;
                    }
                } catch (Exception ex) {
                    getLogger().severe("[Let's Encrypt] Challenge wait error: " + ex.getMessage());
                    acmeChallenges.remove(token);
                    return false;
                }

                acmeChallenges.remove(token);
            }

            KeyPair domainKeyPair;
            if (domainKeyFile.exists()) {
                try (FileReader fr = new FileReader(domainKeyFile)) {
                    domainKeyPair = KeyPairUtils.readKeyPair(fr);
                }
            } else {
                domainKeyPair = KeyPairUtils.createKeyPair(2048);
                try (FileWriter fw = new FileWriter(domainKeyFile)) {
                    KeyPairUtils.writeKeyPair(domainKeyPair, fw);
                }
            }

            CSRBuilder csrb = new CSRBuilder();
            csrb.addDomain(domain);
            csrb.sign(domainKeyPair);

            order.execute(csrb.getEncoded());

            try {
                Status orderStatus = order.waitForCompletion(java.time.Duration.ofSeconds(300));
                if (orderStatus == Status.INVALID) {
                    getLogger().severe("[Let's Encrypt] Order failed. Error: " + order.getError());
                    return false;
                }
                if (orderStatus != Status.VALID) {
                    getLogger().severe("[Let's Encrypt] Order timed out.");
                    return false;
                }
            } catch (Exception ex) {
                getLogger().severe("[Let's Encrypt] Order wait error: " + ex.getMessage());
                return false;
            }

            Certificate certificate = order.getCertificate();
            X509Certificate[] certChain = certificate.getCertificateChain().toArray(new X509Certificate[0]);

            KeyStore ks = KeyStore.getInstance("JKS");
            if (keystoreFile.exists()) {
                try (FileInputStream fis = new FileInputStream(keystoreFile)) {
                    ks.load(fis, keystorePassword.toCharArray());
                }
            } else {
                ks.load(null, keystorePassword.toCharArray());
            }

            ks.setKeyEntry("adminwebdash", domainKeyPair.getPrivate(), keystorePassword.toCharArray(), certChain);

            try (FileOutputStream fos = new FileOutputStream(keystoreFile)) {
                ks.store(fos, keystorePassword.toCharArray());
            }

            getLogger().info("[Let's Encrypt] Successfully obtained and stored certificate for " + domain);
            return true;

        } catch (Exception e) {
            getLogger().severe("[Let's Encrypt] Failed to obtain certificate: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static void startRenewalTask() {
        if (renewalExecutor != null && !renewalExecutor.isShutdown()) return;

        renewalExecutor = Executors.newSingleThreadScheduledExecutor();
        // Check once a day if renewal is needed. Let's Encrypt handles skipping early renewals if cert is valid.
        // Actually acme4j doesn't easily return the expiry of an existing cert without reading it from keystore.
        // A simple approach is to rely on triggering it and acme4j or we can read the keystore.
        renewalExecutor.scheduleAtFixedRate(() -> {
            try {
                if (!AdminWebDashPlugin.isLetsEncrypt()) return;
                
                String domain = AdminWebDashPlugin.getDomain();
                String email = AdminWebDashPlugin.getLetsEncryptEmail();
                String keystorePath = AdminWebDashPlugin.getKeystorePath();
                String keystorePassword = AdminWebDashPlugin.getKeystorePassword();
                
                File keystoreFile = new File(keystorePath);
                if (!keystoreFile.isAbsolute()) {
                    keystoreFile = new File("mods/AdminWebDash", keystorePath);
                }

                if (keystoreFile.exists()) {
                    KeyStore ks = KeyStore.getInstance("JKS");
                    try (FileInputStream fis = new FileInputStream(keystoreFile)) {
                        ks.load(fis, keystorePassword.toCharArray());
                        X509Certificate cert = (X509Certificate) ks.getCertificate("adminwebdash");
                        if (cert != null) {
                            long timeRemaining = cert.getNotAfter().getTime() - System.currentTimeMillis();
                            long daysRemaining = TimeUnit.MILLISECONDS.toDays(timeRemaining);
                            if (daysRemaining > 30) {
                                // Cert is still valid for > 30 days, skip renewal
                                return;
                            }
                            getLogger().info("[Let's Encrypt] Certificate expires in " + daysRemaining + " days. Initiating renewal.");
                        }
                    }
                }
                
                checkAndRenewCertificate(domain, email, keystoreFile, keystorePassword);
            } catch (Exception e) {
                getLogger().severe("[Let's Encrypt] Renewal task failed: " + e.getMessage());
            }
        }, 0, 1, TimeUnit.DAYS);
    }
}
