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

/** DeepSeek Chat Completions client（对照 1.8.9 StepChatTranslationClient，双方向 prompt 一致）。 */
public final class ChatTranslationClient {
    private static final String ENDPOINT = "https://api.deepseek.com/chat/completions";
    private static final String MODEL = "deepseek-v4-flash";
    private static final String API_KEY_ENV = "MICX_DEEPSEEK_API_KEY";
    private static final int RESPONSE_LIMIT = 64 * 1024;
    private static final Set<HttpURLConnection> ACTIVE_CONNECTIONS = ConcurrentHashMap.newKeySet();

    private static final String COMMON_GLOSSARY =
            "Context: AA means Alien Arcadium; preserve round/wave, tactical meaning, quantities, player names and numbers.\n"
          + "Keep exact game tokens: AA, Alien Arcadium, ult, alt, LR, FR, EH, LS, CC, DSG, ZP, TOO, Giant, Clown, blazeZB, eco, rr.\n"
          + "ult/alt are AA defensive positions. Never invent mob counts or replace blazeZB with Clown.\n"
          + "Glossary: Fast Revive→快速救援, Lightning Rod→闪电棒, Rainbow Rifle→彩虹枪, Zombie Zapper→电击器, Max Ammo→满弹, Insta Kill→秒杀, downed→倒地, revive→救援, power/door→电源/门, mob farming→养怪, jammed/reload issue→卡弹/换弹问题, powerup→道具.";

    private ChatTranslationClient() {
    }

    /** 中文聊天输入 → 目标语言发送（出站 ChatTranslate）。 */
    public static String translateOutbound(String source, String configuredKey, int timeoutMs,
                                           TranslationLanguage target) throws TranslationException {
        TranslationLanguage dst = target == null ? TranslationLanguage.EN : target;
        return translate(source, TranslationLanguage.ZH, dst, configuredKey, timeoutMs);
    }

    /** 聊天行自动识别 → 简体中文本地显示（入站 [T] 点击翻译）。 */
    public static String translateToChinese(String source, String configuredKey, int timeoutMs)
            throws TranslationException {
        return translate(source, TranslationLanguage.AUTO, TranslationLanguage.ZH, configuredKey, timeoutMs);
    }

    public static String translate(String source, TranslationLanguage sourceLanguage,
                                   TranslationLanguage targetLanguage, String configuredKey,
                                   int timeoutMs) throws TranslationException {
        TranslationLanguage src = sourceLanguage == null ? TranslationLanguage.AUTO : sourceLanguage;
        TranslationLanguage dst = targetLanguage == null ? TranslationLanguage.EN : targetLanguage;
        if (src != TranslationLanguage.AUTO && src == dst) {
            String safe = ChatTranslationRules.safeOutbound(source);
            if (safe == null) throw new TranslationException("invalid_response");
            return safe;
        }
        return request(source, timeoutMs, resolveKey(configuredKey), buildSystemPrompt(src, dst));
    }

    /** 配置的 key 优先；留空时回退环境变量（Prism 启动通常拿不到 shell 环境变量）。 */
    public static String resolveKey(String configuredKey) {
        if (configuredKey != null && !configuredKey.isBlank()) return configuredKey.trim();
        String env = System.getenv(API_KEY_ENV);
        return env == null ? "" : env.trim();
    }

    private static String request(String source, int timeoutMs, String apiKey,
                                  String systemPrompt) throws TranslationException {
        if (apiKey == null || apiKey.isEmpty()) throw new TranslationException("missing_api_key");
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
            byte[] payload = requestBody(source, systemPrompt).getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(payload.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(payload);
            }
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new TranslationException("http_" + connection.getResponseCode());
            }
            String content = parseContent(readBody(connection));
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

    static String parseContent(String body) throws TranslationException {
        try {
            JsonObject root = new JsonParser().parse(body).getAsJsonObject();
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) throw new TranslationException("missing_content");
            JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            if (message == null || !message.has("content")) throw new TranslationException("missing_content");
            String content = message.get("content").getAsString();
            if (content == null || content.trim().isEmpty()) throw new TranslationException("empty_content");
            return content;
        } catch (TranslationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new TranslationException("invalid_json");
        }
    }

    private static String requestBody(String source, String systemPrompt) {
        JsonObject root = new JsonObject();
        root.addProperty("model", MODEL);
        root.addProperty("temperature", 0.2);
        JsonObject thinking = new JsonObject();
        thinking.addProperty("type", "disabled");
        root.add("thinking", thinking);
        JsonArray messages = new JsonArray();
        messages.add(message("system", systemPrompt));
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

    private static String buildSystemPrompt(TranslationLanguage source, TranslationLanguage target) {
        if (source == TranslationLanguage.ZH && target == TranslationLanguage.EN) {
            return "You are a translator for Hypixel Zombies chat. Translate the user's message from Chinese to English.\n"
                  + "Rules:\n"
                  + "- Output ONLY the English translation, nothing else; never output Chinese\n"
                  + "- Never rewrite the Chinese input into different Chinese words; the output must be genuine English\n"
                  + "- Keep all English words, numbers, abbreviations exactly as they are\n"
                  + COMMON_GLOSSARY + "\n"
                  + "- Positions: ult/alt are AA defensive positions; do not translate them as ordinary words\n"
                  + "- If the input is already English, return it unchanged";
        }
        if (target == TranslationLanguage.ZH && source == TranslationLanguage.AUTO) {
            return "You are a translator for Hypixel Zombies chat. Translate the user's chat message (auto-detect the source language: English, Korean, Japanese, Russian, or any other language) into natural Simplified Chinese.\n"
                  + "Rules:\n"
                  + "- Output ONLY the Chinese translation, nothing else\n"
                  + "- If the input is already Chinese, return it unchanged\n"
                  + "- Preserve the meaning of round/wave, tactical callouts and quantities; never invent mob counts\n"
                  + "- Preserve these exact tokens character-for-character: AA, ult, alt, LR, FR, EH, LS, CC, DSG, ZP, TOO, blazeZB\n"
                  + "- ult and alt are AA defensive positions; keep the literal words ult and alt in the Chinese result.\n"
                  + COMMON_GLOSSARY + "\n"
                  + "- Keep player names, numbers and existing Chinese text unchanged";
        }
        String sourceName = source == TranslationLanguage.AUTO
                ? "the detected source language" : source.promptName();
        String targetName = target == TranslationLanguage.AUTO
                ? "the detected target language" : target.promptName();
        return "You are a translator for Hypixel Zombies chat. Translate the user's message from "
                + sourceName + " to " + targetName + ".\n"
                + "Rules:\n"
                + "- Output ONLY the translation, nothing else; the output MUST be written in " + targetName + "\n"
                + "- Never output the source language or a third language. If the input is Chinese, never rewrite it into different Chinese words: produce a genuine, grammatical "
                + targetName + " sentence that a native speaker would actually say\n"
                + writingRule(target)
                + "- Preserve names, numbers, round/wave, quantities and tactical meaning\n"
                + "- Preserve exact English game tokens (AA, ult, alt, LR, FR, EH, LS, CC, DSG, ZP, TOO, Giant, Clown, blazeZB, eco, rr) character-for-character\n"
                + "- The glossary below is reference only: its Chinese terms must never appear in the output\n"
                + COMMON_GLOSSARY;
    }

    /** Per-target-language writing constraints for the generic prompt branch. */
    private static String writingRule(TranslationLanguage target) {
        return switch (target) {
            case JA -> "- Write natural Japanese: hiragana/katakana with kanji exactly as Japanese is written; kanji like 満弾 or 電源 are fine, but particles and grammar must be Japanese, never a Chinese sentence written in kanji\n";
            case KO -> "- Write natural Korean in Hangul; never write Chinese characters\n";
            case RU -> "- Write natural Russian in Cyrillic\n";
            case ZH -> "- Write natural Simplified Chinese\n";
            default -> "";
        };
    }

    public static final class TranslationException extends Exception {
        public TranslationException(String reason) {
            super(reason);
        }
    }
}
