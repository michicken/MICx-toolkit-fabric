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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
                case "combat_move" -> onClient(client -> JevMoveFix.request(
                        opt(request, "style", ""), optInt(request, "duration_ms", 350),
                        request.has("sprint") && request.get("sprint").getAsBoolean()));
                case "clear_combat_move" -> onClient(JevMoveFix::clear);
                case "poi_use_block" -> onClient(client -> poiUseBlock(client,
                        optInt(request, "x", Integer.MIN_VALUE), optInt(request, "y", Integer.MIN_VALUE),
                        optInt(request, "z", Integer.MIN_VALUE), opt(request, "face", "up"),
                        optFloat(request, "yaw", Float.NaN), optFloat(request, "pitch", Float.NaN)));
                case "poi_click_slot" -> onClient(client -> poiClickSlot(client,
                        optInt(request, "slot", -1), optInt(request, "button", 0),
                        opt(request, "input", "PICKUP")));
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

    /**
     * Performs one evidence-backed POI block interaction through vanilla's normal game-mode path.
     * The external agent may only supply a nearby, recorded block and face; this does not create or
     * send a custom movement/interaction packet.
     */
    private static String poiUseBlock(Minecraft client, int x, int y, int z, String faceName,
                                      float yaw, float pitch) {
        if (client.player == null || client.level == null || client.gameMode == null) return "error: no world";
        if (x == Integer.MIN_VALUE || y == Integer.MIN_VALUE || z == Integer.MIN_VALUE) {
            return "error: missing poi block";
        }
        BlockPos pos = new BlockPos(x, y, z);
        if (client.player.distanceToSqr(Vec3.atCenterOf(pos)) > 36.0) return "error: poi block too far";
        Direction face = Direction.byName(faceName == null ? "" : faceName.toLowerCase());
        if (face == null) face = Direction.UP;
        // Use the recorded normal camera orientation, never a silent server-only rotation.
        if (Float.isFinite(yaw)) {
            client.player.setYRot(yaw);
            client.player.setYHeadRot(yaw);
            client.player.setYBodyRot(yaw);
        }
        if (Float.isFinite(pitch)) client.player.setXRot(Math.max(-90.0f, Math.min(90.0f, pitch)));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false);
        client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
        return "poi-use-block";
    }

    /**
     * Replays exactly one human-observed container input only while a real container is open.
     * Its container id comes from the live menu, never from a stale recording.
     */
    private static String poiClickSlot(Minecraft client, int slot, int button, String inputName) {
        if (client.player == null || client.gameMode == null || client.gui == null
                || !(client.gui.screen() instanceof AbstractContainerScreen<?>)) {
            return "error: no open container";
        }
        if (slot < 0 || slot >= client.player.containerMenu.slots.size()) return "error: container slot out of range";
        ContainerInput input;
        try {
            input = ContainerInput.valueOf(inputName == null ? "PICKUP" : inputName.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return "error: unsupported container input";
        }
        if (input != ContainerInput.PICKUP && input != ContainerInput.QUICK_MOVE) {
            return "error: container input not allowed";
        }
        client.gameMode.handleContainerInput(client.player.containerMenu.containerId, slot,
                Math.max(0, Math.min(1, button)), input, client.player);
        return "poi-container-click";
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

    private static float optFloat(JsonObject request, String key, float fallback) {
        try {
            return request.has(key) ? request.get(key).getAsFloat() : fallback;
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
