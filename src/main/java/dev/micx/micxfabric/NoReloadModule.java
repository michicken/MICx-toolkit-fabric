package dev.micx.micxfabric;

import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * NoReload 免换弹 — Forge NoReloadModule 的 26.2 移植（BridgerCat 原版语义）。
 *
 * <p>按住右键时按固定间隔（默认 67ms）轮换快捷栏武器槽，每次切槽后纯发包模拟
 * "从背包拿武器"让服务端重置武器状态 → 跳过换弹动画：
 * {@code CLICK(36+slot) + [CLICK(41) 放回手上物品] + CLOSE(0)}。</p>
 *
 * <p>26.2 适配点：
 * <ul>
 *   <li>1.8.9 的 C0B(OPEN_INVENTORY)/C0D 握手在现代协议中不存在：开背包不再是包；
 *       序列退化为 {@link ServerboundContainerClickPacket}(windowId=0) ×2
 *       + {@link ServerboundContainerClosePacket}(0)（VFP 负责翻译回 C0E/C0D）。
 *       1.8.9 的 actionNumber 被现代 stateId 取代，直接取 inventoryMenu.getStateId()。</li>
 *   <li>必须走 {@code connection.send()} 原始发包——gameMode.handleContainerInput 会先跑
 *       本地 AbstractContainerMenu.clicked 模拟，真的把物品挪进光标，不能碰。</li>
 *   <li>切槽后显式补发 {@link ServerboundSetCarriedItemPacket}：现代客户端只在
 *       useItem/attack 等路径前 ensureHasSentCarriedItem，纯发包序列不会触发它。</li>
 *   <li>物理右键用 GLFW 轮询（无 LWJGL2 Mouse）；暂停判定同原版：GUI / 盔甲架 ≤3 格 /
 *       箱子 / 槽 1；发包节流 12ms，暂停恢复重置计时防 dt 跳变。</li>
 * </ul></p>
 *
 * <p>与 KeyboardClicker(AutoSwitch) 互斥（ModuleRuntime.registerMutex）：双切槽器冲突，
 * 且本地 itemDamage 不变会让连点器防卡弹误判。默认关闭。</p>
 */
public final class NoReloadModule implements Module {
    private static final NoReloadModule INSTANCE = new NoReloadModule();
    private static final int DEFAULT_KEY = GLFW.GLFW_KEY_N;

    private boolean enabled;
    private double delayMs = NoReloadRules.DELAY_DEFAULT_MS;
    private boolean slot2 = true;
    private boolean slot3 = false;
    private boolean slot4 = false;
    private boolean rrMode = false;
    private InputBinding binding = new InputBinding(DEFAULT_KEY);
    private boolean configLoaded;

    /* ---- 运行时状态（原版照搬） ---- */
    private long lastTickNs = 0L;
    private long accNs = 0L;
    private long nextAllowedSwitchNs = 0L;
    private long intervalNs = NoReloadRules.msToNanos(NoReloadRules.DELAY_DEFAULT_MS);
    private boolean wasRmbDown = false;
    private boolean pausedForArmorStand = false;
    private boolean pausedForChest = false;
    private boolean pausedForSlot1 = false;
    private final List<Integer> activeSlots = new ArrayList<>();
    private final List<Integer> validSlots = new ArrayList<>();
    private int currentSlotIndex = 0;

    private NoReloadModule() {
    }

    public static NoReloadModule instance() {
        return INSTANCE;
    }

    @Override
    public String id() {
        return "noreload";
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public boolean defaultEnabled() {
        return false;
    }

    @Override
    public void setEnabled(boolean value) {
        loadConfig();
        this.enabled = value;
        if (value) resetState();
        ModuleStateStore.put(id(), value);
    }

    @Override
    public InputBinding primaryBinding() {
        loadConfig();
        return binding;
    }

    @Override
    public void onPrimaryPressed(Minecraft client, boolean newlyEnabled) {
        if (newlyEnabled) {
            notify(client, "NoReload ON");
        } else {
            ModuleRuntime.setEnabled(id(), false);
            notify(client, "NoReload OFF");
        }
    }

    @Override
    public void resetState() {
        currentSlotIndex = 0;
        wasRmbDown = false;
        pausedForArmorStand = false;
        pausedForChest = false;
        pausedForSlot1 = false;
        accNs = 0L;
        lastTickNs = 0L;
        nextAllowedSwitchNs = 0L;
        activeSlots.clear();
        validSlots.clear();
        rebuildActiveSlots();
        updateValidSlots();
        intervalNs = NoReloadRules.msToNanos(delayMs);
    }

    /* ---- 主循环（原版 onClientTick START 相位 → END_CLIENT_TICK） ---- */

    @Override
    public void tick(Minecraft client) {
        if (!enabled || client == null || client.player == null || client.level == null) return;
        if (client.gui.screen() != null) return;   // GUI 打开不发包不切槽

        if (shouldPauseForArmorStand(client)) {
            wasRmbDown = isRightButtonPhysicallyDown(client);
            pausedForArmorStand = true;
            return;
        }
        if (pausedForArmorStand) {
            pausedForArmorStand = false;
            lastTickNs = System.nanoTime();      // 恢复重置计时，防 dt 跳变
            return;
        }

        if (shouldPauseForChest(client)) {
            wasRmbDown = isRightButtonPhysicallyDown(client);
            pausedForChest = true;
            return;
        }
        if (pausedForChest) {
            pausedForChest = false;
            lastTickNs = System.nanoTime();
            return;
        }

        int selected = client.player.getInventory().getSelectedSlot();
        if (NoReloadRules.isSlot1(selected)) {
            wasRmbDown = isRightButtonPhysicallyDown(client);
            pausedForSlot1 = true;
            return;
        }
        if (pausedForSlot1) {
            pausedForSlot1 = false;
            lastTickNs = System.nanoTime();
            return;
        }

        updateValidSlots();
        boolean rmbDown = isRightButtonPhysicallyDown(client);
        long now = System.nanoTime();
        if (lastTickNs == 0L) lastTickNs = now;
        long dt = now - lastTickNs;
        lastTickNs = now;
        accNs += dt;

        // 右键边沿：重建序列 → 定位当前槽 → 切下一把 + 发包（首切快：accNs = interval/2）
        if (rmbDown && !wasRmbDown) {
            rebuildActiveSlots();
            updateValidSlots();
            if (!validSlots.isEmpty()) {
                currentSlotIndex = NoReloadRules.nextSlotIndex(validSlots,
                        client.player.getInventory().getSelectedSlot(), -1);
                int targetSlot = validSlots.get(currentSlotIndex);
                switchToSlot(client, targetSlot);
                if (rrMode && goldenShovelSlot(client) == targetSlot) {
                    nextAllowedSwitchNs = 0L;    // RRMode：金铲子槽不发包（近战无需重置）
                } else {
                    clickSlotThroughInventory(client, targetSlot);
                    nextAllowedSwitchNs = now + NoReloadRules.SWITCH_THROTTLE_NS;
                }
            }
            intervalNs = NoReloadRules.msToNanos(delayMs);
            accNs = intervalNs / 2L;
            lastTickNs = now;
        }

        // 持续按住：到间隔切下一把 + 发包（节流期内只扣时间不切）
        if (rmbDown && !validSlots.isEmpty() && accNs >= intervalNs) {
            if (now < nextAllowedSwitchNs) {
                accNs -= intervalNs;
            } else {
                accNs -= intervalNs;
                currentSlotIndex = NoReloadRules.nextSlotIndex(validSlots,
                        client.player.getInventory().getSelectedSlot(), currentSlotIndex);
                int nextSlot = validSlots.get(currentSlotIndex);
                switchToSlot(client, nextSlot);
                if (rrMode && goldenShovelSlot(client) == nextSlot) {
                    nextAllowedSwitchNs = 0L;
                } else {
                    clickSlotThroughInventory(client, nextSlot);
                    nextAllowedSwitchNs = now + NoReloadRules.SWITCH_THROTTLE_NS;
                }
            }
        }
        wasRmbDown = rmbDown;
    }

    /* ---- 暂停判定（原版） ---- */

    private boolean shouldPauseForArmorStand(Minecraft client) {
        if (client.hitResult == null || !(client.hitResult instanceof EntityHitResult hit)) return false;
        if (hit.getEntity() instanceof ArmorStand stand) {
            return NoReloadRules.pauseForArmorStand(client.player.distanceTo(stand));
        }
        return false;
    }

    private boolean shouldPauseForChest(Minecraft client) {
        if (client.hitResult == null || !(client.hitResult instanceof BlockHitResult hit)) return false;
        return client.level.getBlockState(hit.getBlockPos()).getBlock() instanceof net.minecraft.world.level.block.ChestBlock;
    }

    private static boolean isRightButtonPhysicallyDown(Minecraft client) {
        try {
            return GLFW.glfwGetMouseButton(client.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT)
                    == GLFW.GLFW_PRESS;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /* ---- 槽位逻辑（原版） ---- */

    private void rebuildActiveSlots() {
        activeSlots.clear();
        activeSlots.addAll(NoReloadRules.buildActiveSlots(slot2, slot3, slot4));
    }

    private void updateValidSlots() {
        validSlots.clear();
        for (int slot : activeSlots) {
            if (isSlotValid(slot)) validSlots.add(slot);
        }
        if (currentSlotIndex >= validSlots.size()) currentSlotIndex = 0;
    }

    private boolean isSlotValid(int hotbarSlot) {
        Minecraft client = Minecraft.getInstance();
        ItemStack stack = client.player.getInventory().getItem(hotbarSlot);
        boolean empty = stack == null || stack.isEmpty();
        boolean silverDye = !empty && stack.is(Items.DYE.pick(DyeColor.LIGHT_GRAY));
        return NoReloadRules.isSlotValid(empty, silverDye);
    }

    /** 快捷栏 1-3 槽中的第一个金铲子槽（0 起索引，返回 1/2/3）；无则 -1。 */
    private static int goldenShovelSlot(Minecraft client) {
        for (int i = 1; i <= 3; i++) {
            ItemStack stack = client.player.getInventory().getItem(i);
            if (stack != null && !stack.isEmpty() && stack.is(Items.GOLDEN_SHOVEL)) return i;
        }
        return -1;
    }

    private static void switchToSlot(Minecraft client, int hotbarSlot) {
        var inventory = client.player.getInventory();
        if (inventory.getSelectedSlot() != hotbarSlot) {
            inventory.setSelectedSlot(hotbarSlot);
            ClientPacketListener connection = client.getConnection();
            // 纯发包路径不会触发 ensureHasSentCarriedItem，显式补发选中槽同步
            if (connection != null) connection.send(new ServerboundSetCarriedItemPacket(hotbarSlot));
        }
    }

    /* ---- 发包（Bridger 原版 clickSlotThroughInventory 的 26.2 等价） ----
     * 包序列 = CLICK(windowId=0, 36+slot 取武器) → [CLICK(41 放回手上物品)]
     * → CLOSE(0)。1.8.9 的 C0B OPEN_INVENTORY 与 actionNumber 在现代协议不存在，
     * 由 stateId 取代；走 connection.send 原始发包避免本地菜单模拟真搬物品。 */

    private static void clickSlotThroughInventory(Minecraft client, int hotbarSlot) {
        ClientPacketListener connection = client.getConnection();
        if (connection == null || client.player == null) return;
        try {
            int windowId = 0;   // InventoryMenu.CONTAINER_ID（玩家背包）
            int stateId = client.player.inventoryMenu.getStateId();
            ItemStack heldItem = client.player.getInventory().getSelectedItem();
            HashedStack carried = heldItem.isEmpty() ? HashedStack.EMPTY
                    : HashedStack.create(heldItem.copy(), connection.decoratedHashOpsGenenerator());
            connection.send(new ServerboundContainerClickPacket(windowId, stateId,
                    (short) NoReloadRules.inventorySlotFor(hotbarSlot), (byte) 0,
                    ContainerInput.PICKUP, Int2ObjectMaps.emptyMap(), HashedStack.EMPTY));
            if (!heldItem.isEmpty()) {
                connection.send(new ServerboundContainerClickPacket(windowId, stateId,
                        (short) NoReloadRules.STASH_SLOT, (byte) 0,
                        ContainerInput.PICKUP, Int2ObjectMaps.emptyMap(), carried));
            }
            connection.send(new ServerboundContainerClosePacket(windowId));
        } catch (RuntimeException ex) {
            // 原版空 catch：发包异常静默（防崩溃）
        }
    }

    private static void notify(Minecraft client, String text) {
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(ChatMessageStyles.notice("NoReload: "
                    + (text.endsWith("ON") ? "ON" : "OFF")));
        }
    }

    /* ---- 配置持久化 ---- */

    public double getDelayMs() {
        loadConfig();
        return delayMs;
    }

    public void setDelayMs(double v) {
        delayMs = Math.max(NoReloadRules.DELAY_MIN_MS, Math.min(NoReloadRules.DELAY_MAX_MS, v));
        intervalNs = NoReloadRules.msToNanos(delayMs);
        saveConfig();
    }

    public boolean isSlot2Enabled() { loadConfig(); return slot2; }
    public void setSlot2(boolean v) { slot2 = v; saveConfig(); }
    public boolean isSlot3Enabled() { loadConfig(); return slot3; }
    public void setSlot3(boolean v) { slot3 = v; saveConfig(); }
    public boolean isSlot4Enabled() { loadConfig(); return slot4; }
    public void setSlot4(boolean v) { slot4 = v; saveConfig(); }
    public boolean isRrMode() { loadConfig(); return rrMode; }
    public void setRrMode(boolean v) { rrMode = v; saveConfig(); }

    public int keyCode() {
        loadConfig();
        return binding.code();
    }

    public void setKeyCode(int code) {
        loadConfig();
        binding.setCode(code == 0 ? GLFW.GLFW_KEY_UNKNOWN : code);
        saveConfig();
    }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Path current = FabricRuntime.configPath().resolve("noreload.properties");
        Path legacy = FabricRuntime.configPath().getParent().resolve("MICxToolkit_NoReload.cfg");
        Properties properties = ConfigProperties.load(current, legacy);
        delayMs = ConfigProperties.real(properties, "delayMs",
                NoReloadRules.DELAY_DEFAULT_MS, NoReloadRules.DELAY_MIN_MS, NoReloadRules.DELAY_MAX_MS);
        slot2 = ConfigProperties.bool(properties, "slot2", true);
        slot3 = ConfigProperties.bool(properties, "slot3", false);
        slot4 = ConfigProperties.bool(properties, "slot4", false);
        rrMode = ConfigProperties.bool(properties, "rrMode", false);
        binding = new InputBinding(ConfigProperties.integer(properties, "keyCode", DEFAULT_KEY,
                -108, GLFW.GLFW_KEY_LAST));
        intervalNs = NoReloadRules.msToNanos(delayMs);
    }

    private void saveConfig() {
        Properties properties = new Properties();
        properties.setProperty("delayMs", Double.toString(delayMs));
        properties.setProperty("slot2", Boolean.toString(slot2));
        properties.setProperty("slot3", Boolean.toString(slot3));
        properties.setProperty("slot4", Boolean.toString(slot4));
        properties.setProperty("rrMode", Boolean.toString(rrMode));
        properties.setProperty("keyCode", Integer.toString(binding.code()));
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("noreload.properties"), properties,
                    "MICx NoReload configuration");
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to save NoReload configuration", exception);
        }
    }
}
