package dev.micx.micxfabric;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.util.Base64;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/** Signed TeamSync token fetcher with bounded input and an owned worker lifecycle. */
public final class TeamSyncTokenProvider implements AutoCloseable {
    private static final int TIMEOUT_MS = 8_000;
    private static final int MAX_BODY_BYTES = 64 * 1024;
    private static final long REFRESH_MARGIN_MS = 5 * 60 * 1_000L;
    private static final String PUBLIC_KEY_PEM =
            "-----BEGIN PUBLIC KEY-----\n"
                    + "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA1Mq147intdgg6rL2x4P/\n"
                    + "pJxmkWHl1x8GUME7khtrA+/dLp+N0FeXnSfyg06JWvRgX3uW7t9A/GU481YKph8V\n"
                    + "yviHmRJtgbYkT9LnXazlKR7uEnvkH5J8lVrYfvqzaMneb+bWndqPuGzR8c5563em\n"
                    + "XnVBZgI2YjLtoabrlZi01z+C2HsrngP8yxH8xTIdOswajpFMU2HbVPTvMO3QOHE5\n"
                    + "dFVOevnbH/q3QdDujmD0qkgJtflbthJoKTRe2FD0I9do600uoxUXELaSdd9v9JNP\n"
                    + "d8xddF9Mv90fSIM+D58Zl5PEW7Uz4XeYYcsAl1eTweKONm3DIo2A3ZGwc4+wts0S\n"
                    + "BwIDAQAB\n"
                    + "-----END PUBLIC KEY-----";

    private final Gson gson = new Gson();
    private final String tokenUrl;
    private final ExecutorService executor;
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private volatile String cachedJson;
    private volatile long cachedExpMs;
    private volatile Future<?> refreshTask;
    private volatile boolean closed;

    public TeamSyncTokenProvider(String tokenUrl) {
        this.tokenUrl = TeamSyncConfig.safeUrl(tokenUrl, "", "https");
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "MICx-TeamSync-Token");
            thread.setDaemon(true);
            return thread;
        });
    }

    public JsonObject getTokenNonBlocking() {
        if (closed) return null;
        long now = System.currentTimeMillis();
        String json = cachedJson;
        if (json == null || cachedExpMs - now <= REFRESH_MARGIN_MS) refreshAsync();
        if (json == null || cachedExpMs <= now) return null;
        try {
            return gson.fromJson(json, JsonObject.class);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** Returns the legacy HS HTTP bearer: Base64 of the complete signed token JSON. */
    public String getBearerNonBlocking() {
        JsonObject token = getTokenNonBlocking();
        return token == null ? null : encodeBearer(token);
    }

    static String encodeBearer(JsonObject token) {
        if (token == null) return null;
        return Base64.getEncoder().encodeToString(
                token.toString().getBytes(StandardCharsets.UTF_8));
    }

    public void refreshAsync() {
        if (closed || tokenUrl.isBlank() || !refreshing.compareAndSet(false, true)) return;
        refreshTask = executor.submit(() -> {
            try {
                fetch();
            } finally {
                refreshing.set(false);
            }
        });
    }

    private synchronized void fetch() {
        if (closed) return;
        HttpsURLConnection connection = null;
        try {
            URI uri = URI.create(tokenUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) return;
            connection = (HttpsURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            if (connection.getResponseCode() != HttpsURLConnection.HTTP_OK) return;
            String body = readBounded(connection.getInputStream());
            JsonObject object = gson.fromJson(body, JsonObject.class);
            if (object == null || !verify(object)) return;
            long expSeconds = object.get("exp").getAsLong();
            long expMs = Math.multiplyExact(expSeconds, 1_000L);
            if (expMs <= System.currentTimeMillis()) return;
            cachedJson = body;
            cachedExpMs = expMs;
        } catch (Exception ignored) {
            // Token contents and endpoint responses must never reach logs.
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String readBounded(InputStream stream) throws Exception {
        try (InputStream input = stream;
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[2048];
            int total = 0;
            int count;
            while ((count = reader.read(buffer)) >= 0) {
                total += count;
                if (total > MAX_BODY_BYTES) throw new IllegalStateException("response too large");
                builder.append(buffer, 0, count);
            }
            return builder.toString();
        }
    }

    private static boolean verify(JsonObject object) {
        try {
            if (!object.has("token") || !object.has("exp") || !object.has("sig")) return false;
            String token = object.get("token").getAsString();
            long exp = object.get("exp").getAsLong();
            byte[] signatureBytes = Base64.getDecoder().decode(object.get("sig").getAsString());
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(loadPublicKey());
            signature.update((token + ":" + exp).getBytes(StandardCharsets.UTF_8));
            return signature.verify(signatureBytes);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static PublicKey loadPublicKey() throws Exception {
        String base64 = PUBLIC_KEY_PEM.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", "");
        return KeyFactory.getInstance("RSA").generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
    }

    @Override
    public void close() {
        closed = true;
        Future<?> task = refreshTask;
        if (task != null) task.cancel(true);
        executor.shutdownNow();
        cachedJson = null;
        cachedExpMs = 0L;
    }
}
