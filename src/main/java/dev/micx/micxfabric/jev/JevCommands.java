package dev.micx.micxfabric.jev;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import dev.micx.micxfabric.AimbotConfig;
import dev.micx.micxfabric.AimbotModule;
import dev.micx.micxfabric.ModuleRuntime;
import dev.micx.micxfabric.RemoteShopModule;
import dev.micx.micxfabric.ReviveAuraModule;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 桥的指令实现：每个 op 都跑在客户端线程上（世界数据只有主线程能安全读），
 * HTTP 线程阻塞等结果（≤1.5s）。任何异常都变成 {@code ok:false} 的字符串回给 agent，不往上抛。
 */
public final class JevCommands {
    private static final long CLIENT_TIMEOUT_MS = 1500L;

    private JevCommands() {
    }

    public static JsonObject execute(JsonObject request) {
        String op = opt(request, "op", "noop");
        JsonObject out = new JsonObject();
        out.addProperty("op", op);
        try {
            String result = switch (op) {
                case "noop", "ping" -> "pong";
                case "module" -> setModule(request);
                case "goto" -> onClient(client -> BaritoneBridge.gotoNear(
                        num(request, "x"), num(request, "y"), num(request, "z"), optInt(request, "range", 2)));
                case "goto_block" -> onClient(client -> BaritoneBridge.gotoBlock(
                        num(request, "x"), num(request, "y"), num(request, "z")));
                case "follow" -> onClient(client -> BaritoneBridge.follow(opt(request, "name", "")));
                case "stop_path" -> onClient(client -> BaritoneBridge.stop());
                case "buy_now" -> onClient(client -> RemoteShopModule.instance().triggerNearest(client));
                case "scan_shops" -> scanShops(request);
                case "chat" -> onClient(client -> {
                    if (client.getConnection() == null) return "error: no connection";
                    String text = opt(request, "text", "");
                    if (text.isEmpty()) return "error: empty text";
                    client.getConnection().sendChat(text);
                    return "ok";
                });
                case "use_slot" -> onClient(client -> useSlot(client, optInt(request, "slot", 0)));
                case "hold_use" -> onClient(client -> holdUse(client,
                        request.has("on") && request.get("on").getAsBoolean()));
                case "close_screen" -> onClient(JevCommands::closeScreen);
                case "calibrate_window" -> onClient(client -> JevWindowAnchors.calibrate(
                        opt(request, "id", ""), client.player));
                case "set" -> set(request);
                default -> "error: unknown op '" + op + "'";
            };
            out.addProperty("ok", !result.startsWith("error") && !result.startsWith("timeout"));
            out.addProperty("result", result);
        } catch (Throwable t) {
            out.addProperty("ok", false);
            out.addProperty("error", String.valueOf(t));
        }
        return out;
    }

    private static String setModule(JsonObject request) {
        String id = opt(request, "id", "");
        boolean on = request.has("on") && request.get("on").getAsBoolean();
        if (id.isEmpty()) return "error: missing id";
        if (id.equals("jev_bridge")) return on ? "already-on" : "error: cannot disable the bridge from itself";
        if (ModuleRuntime.get(id) == null) return "error: unknown module '" + id + "'";
        ModuleRuntime.setEnabled(id, on);
        return "ok";
    }

    private static String scanShops(JsonObject request) {
        boolean force = request.has("force") && request.get("force").getAsBoolean();
        return onClient(client -> {
            JsonArray array = new JsonArray();
            for (RemoteShopModule.Candidate candidate : RemoteShopModule.instance().scan(client, force)) {
                JsonObject o = new JsonObject();
                o.addProperty("id", candidate.entityId());
                o.addProperty("name", candidate.name());
                o.addProperty("kind", candidate.kind());
                o.addProperty("dist", Math.round(candidate.distance() * 100.0) / 100.0);
                o.addProperty("matched", candidate.matched());
                array.add(o);
            }
            return array.toString();
        });
    }

    private static String useSlot(Minecraft client, int slot) {
        if (slot < 0 || slot > 8) return "error: slot out of range";
        if (client.player == null) return "error: no player";
        // 与 KeyboardClickerModule 同款：模拟按 1-9，走 KeyMapping 当前绑定索引，左右键互换也不误伤
        KeyMapping.click(InputConstants.getKey(new KeyEvent(GLFW.GLFW_KEY_1 + slot, 0, 0)));
        return "ok";
    }

    /**
     * 虚拟按住「使用键」（默认右键）。
     *
     * <p>免手开火的关键：`KeyMapping.setDown(true)` 让 `options.keyUse.isDown()` 为真，
     * 于是 (a) Aimbot 在 `onlyFire=true` 且 `holdLock=false` 时的门控通过，
     * (b) 右键连点器（RightClicker）开始以 CPS 节奏点使用键 = 等效持续开火。
     *
     * <p>注意原版在打开界面时会 releaseAll 把所有键抬起，所以调用方要周期性重按（代理每 2s 重发一次）。
     */
    private static String holdUse(Minecraft client, boolean on) {
        if (client.options == null || client.options.keyUse == null) return "error: no use key";
        client.options.keyUse.setDown(on);
        return on ? "held" : "released";
    }

    /**
     * 关掉当前界面。按住使用键时点到商店/箱子会弹容器界面，而界面一开
     * Aimbot 的 {@code isActiveHere} 与连点器的 {@code shouldFire} 都会直接返回 false——等于全停摆。
     * 容器界面走原版 {@code onClose()}（会正确给服务端发关容器包），其他界面直接清屏。
     */
    private static String closeScreen(Minecraft client) {
        if (client.gui == null) return "error: no gui";
        var screen = client.gui.screen();
        if (screen == null) return "no-screen";
        try {
            if (screen instanceof AbstractContainerScreen<?> containerScreen) {
                containerScreen.onClose();
                return "closed-container";
            }
        } catch (Throwable ignored) {
        }
        try {
            client.setScreenAndShow(null);
            return "closed-screen";
        } catch (Throwable t) {
            return "error: " + t;
        }
    }

    private static String set(JsonObject request) {
        String key = opt(request, "key", "");
        return switch (key) {
            case "revive_aura.range" -> {
                ReviveAuraModule.instance().setRange(num(request, "value"));
                yield "ok";
            }
            case "revive_aura.intervalMs" -> {
                ReviveAuraModule.instance().setIntervalMs(num(request, "value"));
                yield "ok";
            }
            case "remote_shop.keywords" -> {
                RemoteShopModule.instance().setKeywords(opt(request, "value", ""));
                yield "ok";
            }
            case "aimbot.brute" -> aimbotBool(config -> config.bruteMode = bool(request));
            case "aimbot.sweep" -> aimbotBool(config -> config.bruteSweep = bool(request));
            case "aimbot.onlyFire" -> aimbotBool(config -> config.onlyFire = bool(request));
            case "aimbot.holdLock" -> aimbotBool(config -> config.holdLock = bool(request));
            case "aimbot.zombiesOnly" -> aimbotBool(config -> config.zombiesOnly = bool(request));
            case "aimbot.humanize" -> aimbotBool(config -> config.humanize = bool(request));
            default -> "error: unknown key '" + key + "'";
        };
    }

    private static String aimbotBool(Consumer<AimbotConfig> mutator) {
        AimbotConfig config = AimbotModule.instance().config();
        config.load();
        mutator.accept(config);
        config.save();
        return "ok";
    }

    private static String onClient(Function<Minecraft, String> action) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return "error: client not ready";
        CompletableFuture<String> future = new CompletableFuture<>();
        client.execute(() -> {
            try {
                future.complete(action.apply(client));
            } catch (Throwable t) {
                future.complete("error: " + t);
            }
        });
        try {
            return future.get(CLIENT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            return "timeout: game thread did not respond in " + CLIENT_TIMEOUT_MS + "ms";
        } catch (Exception e) {
            return "error: " + e;
        }
    }

    private static String opt(JsonObject request, String key, String fallback) {
        try {
            return request.has(key) && !request.get(key).isJsonNull()
                    ? request.get(key).getAsString()
                    : fallback;
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static double num(JsonObject request, String key) {
        try {
            return request.get(key).getAsDouble();
        } catch (Throwable t) {
            return 0.0;
        }
    }

    private static int optInt(JsonObject request, String key, int fallback) {
        try {
            return request.has(key) ? request.get(key).getAsInt() : fallback;
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static boolean bool(JsonObject request) {
        try {
            return request.get("value").getAsBoolean();
        } catch (Throwable t) {
            return false;
        }
    }
}
