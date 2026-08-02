package dev.micx.micxfabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ChatTranslationClient {
    private static final String ENDPOINT = "https://api.deepseek.com/chat/completions";
    private static final String MODEL = "deepseek-v4-flash";
    private static final String API_KEY_ENV = "MICX_DEEPSEEK_API_KEY";
    private static final int RESPONSE_LIMIT = 64 * 1024;
    private static final Set<HttpURLConnection> ACTIVE_CONNECTIONS = ConcurrentHashMap.newKeySet();

    private ChatTranslationClient() {
    }

    public static String translate(String source, int timeoutMs) throws TranslationException {
        String apiKey = System.getenv(API_KEY_ENV);
        if (apiKey == null || apiKey.isBlank()) throw new TranslationException("missing_api_key");
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
            ACTIVE_CONNECTIONS.add(connection);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            byte[] request = requestBody(source).getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(request.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(request);
            }
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new TranslationException("http_" + connection.getResponseCode());
            }
            String body = readBody(connection);
            JsonObject root = new JsonParser().parse(body).getAsJsonObject();
            JsonArray choices = root.getAsJsonArray("choices");
            String content = choices.get(0).getAsJsonObject().getAsJsonObject("message")
                    .get("content").getAsString();
            String safe = ChatTranslationRules.safeOutbound(content);
            if (safe == null) throw new TranslationException("invalid_response");
            return safe;
        } catch (TranslationException exception) {
            throw exception;
        } catch (java.net.SocketTimeoutException exception) {
            throw new TranslationException("timeout");
        } catch (Exception exception) {
            throw new TranslationException("network_error");
        } finally {
            if (connection != null) {
                ACTIVE_CONNECTIONS.remove(connection);
                connection.disconnect();
            }
        }
    }

    public static void cancelActiveRequests() {
        for (HttpURLConnection connection : ACTIVE_CONNECTIONS) {
            try {
                connection.disconnect();
            } catch (RuntimeException ignored) {
            }
        }
        ACTIVE_CONNECTIONS.clear();
    }

    private static String requestBody(String source) {
        JsonObject root = new JsonObject();
        root.addProperty("model", MODEL);
        root.addProperty("temperature", 0.2);
        JsonObject thinking = new JsonObject();
        thinking.addProperty("type", "disabled");
        root.add("thinking", thinking);
        JsonArray messages = new JsonArray();
        messages.add(message("system", "Translate Chinese game chat to English. Output only the translation. Keep AA, ult, LR, FR, EH, LS, TOO, CC, DSG and ZP unchanged."));
        messages.add(message("user", source));
        root.add("messages", messages);
        return root.toString();
    }

    private static JsonObject message(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message;
    }

    private static String readBody(HttpURLConnection connection) throws Exception {
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            char[] buffer = new char[1024];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                if (body.length() + read > RESPONSE_LIMIT) throw new TranslationException("response_too_large");
                body.append(buffer, 0, read);
            }
        }
        return body.toString();
    }

    public static final class TranslationException extends Exception {
        public TranslationException(String reason) {
            super(reason);
        }
    }
}
