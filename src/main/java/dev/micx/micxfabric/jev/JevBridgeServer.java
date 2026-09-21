package dev.micx.micxfabric.jev;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * 只绑回环地址的极小 HTTP 服务：状态出、指令进。
 *
 * <p>用 JDK 自带 {@code com.sun.net.httpserver}（Prism 的 Mojang 运行时里 jdk.httpserver 在），
 * 不引第三方依赖；工作线程是 daemon，游戏退出不会被它拖住。
 */
public final class JevBridgeServer {
    private static final int MAX_BODY_BYTES = 64 * 1024;

    private final HttpServer server;
    private final int port;

    private JevBridgeServer(HttpServer server, int port) {
        this.server = server;
        this.port = port;
    }

    public static JevBridgeServer start(JevBridgeModule module, int port) throws IOException {
        HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        JevBridgeServer bridge = new JevBridgeServer(http, port);
        http.createContext("/health", bridge::handleHealth);
        http.createContext("/state", bridge::handleState);
        http.createContext("/cmd", bridge::handleCommand);
        http.createContext("/modules", bridge::handleModules);
        http.setExecutor(Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "micx-jev-bridge");
            thread.setDaemon(true);
            return thread;
        }));
        http.start();
        return bridge;
    }

    public void stop() {
        server.stop(0);
    }

    public int port() {
        return port;
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        JsonObject out = new JsonObject();
        out.addProperty("ok", true);
        out.addProperty("port", port);
        out.addProperty("t", System.currentTimeMillis());
        respond(exchange, 200, out.toString());
    }

    private void handleState(HttpExchange exchange) throws IOException {
        JevBridgeModule module = JevBridgeModule.instance();
        String snapshot = module.snapshotJson();
        if (snapshot == null) {
            JsonObject out = new JsonObject();
            out.addProperty("ok", false);
            out.addProperty("reason", "no-snapshot-yet");
            out.addProperty("module_enabled", module.enabled());
            respond(exchange, 200, out.toString());
            return;
        }
        String body;
        try {
            JsonObject out = JsonParser.parseString(snapshot).getAsJsonObject();
            out.addProperty("age_ms", System.currentTimeMillis() - module.snapshotAt());
            body = out.toString();
        } catch (Throwable t) {
            // 快照坏了也要把原文给出去，方便在 agent 侧看到到底错在哪
            body = snapshot;
        }
        respond(exchange, 200, body);
    }

    private void handleCommand(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "{\"ok\":false,\"error\":\"POST only\"}");
            return;
        }
        JsonObject out = new JsonObject();
        try {
            String body = readBody(exchange);
            JsonObject request = JsonParser.parseString(body).getAsJsonObject();
            out = JevCommands.execute(request);
        } catch (Throwable t) {
            out.addProperty("ok", false);
            out.addProperty("error", String.valueOf(t));
        }
        respond(exchange, 200, out.toString());
    }

    private void handleModules(HttpExchange exchange) throws IOException {
        JsonObject out = new JsonObject();
        out.add("modules", JevBridgeModule.moduleList());
        respond(exchange, 200, out.toString());
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            byte[] data = in.readNBytes(MAX_BODY_BYTES);
            return new String(data, StandardCharsets.UTF_8);
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(data);
        } finally {
            exchange.close();
        }
    }
}
