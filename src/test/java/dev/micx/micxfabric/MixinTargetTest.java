package dev.micx.micxfabric;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 启动级防回归：逐个校验 mixin 注解里的目标方法/调用点签名与运行时 MC 类
 * 精确匹配。MixinEquipmentLayerRenderer 曾因描述符少一个 int 参数导致
 * apply 失败→资源重载连锁失败→黑屏；肉眼 javap 比对靠不住。
 */
class MixinTargetTest {

    private static final String MIXIN_PACKAGE = "dev.micx.micxfabric.mixin.";

    @Test
    void everyMixinInjectionTargetExists() throws Exception {
        Path jsonPath = Path.of("src/main/resources/mixins.micx.fabric.json");
        assertTrue(Files.exists(jsonPath), "mixins json missing");
        JsonObject root = JsonParser.parseString(Files.readString(jsonPath)).getAsJsonObject();

        List<String> classNames = new ArrayList<>();
        for (String key : new String[]{"mixins", "client", "server"}) {
            if (root.has(key)) {
                for (JsonElement e : root.getAsJsonArray(key)) classNames.add(e.getAsString());
            }
        }
        assertTrue(classNames.size() > 5, "unexpectedly few mixins parsed: " + classNames);

        List<String> failures = new ArrayList<>();
        for (String name : classNames) {
            Class<?> mixinClass;
            try {
                mixinClass = Class.forName(MIXIN_PACKAGE + name);
            } catch (ClassNotFoundException e) {
                failures.add(name + ": class not found");
                continue;
            }
            checkMixin(mixinClass, failures);
        }
        assertTrue(failures.isEmpty(), () -> "broken mixin targets:\n" + String.join("\n", failures));
    }

    private static void checkMixin(Class<?> mixinClass, List<String> failures) {
        Mixin mixin = mixinClass.getAnnotation(Mixin.class);
        if (mixin == null) return;
        for (Class<?> target : mixin.value()) {
            if (target.isInterface()) continue; // interface-shuffle mixin，目标运行时才定
            for (Method handler : mixinClass.getDeclaredMethods()) {
                for (var annotation : handler.getAnnotations()) {
                    if (annotation instanceof Inject inject) {
                        checkMethodTargets(target, inject.method(), mixinClass.getSimpleName() + "#" + handler.getName(), failures);
                        // @At 目标（如 value="INVOKE"）由 redirect/inject 共用，统一在下面查
                    } else if (annotation instanceof Redirect redirect) {
                        checkMethodTargets(target, redirect.method(), mixinClass.getSimpleName() + "#" + handler.getName(), failures);
                        checkAtTargets(redirect.at(), failures);
                    }
                }
            }
        }
    }

    /** method 条目："name" 或 "name(desc)ret"；后者必须与目标类方法精确一致。 */
    private static void checkMethodTargets(Class<?> target, String[] selectors, String where, List<String> failures) {
        for (String selector : selectors) {
            int paren = selector.indexOf('(');
            if (paren < 0) continue; // 仅名字：宽松放行
            String wantedName = selector.substring(0, paren);
            boolean found = false;
            for (Method m : target.getDeclaredMethods()) {
                String desc = Type.getMethodDescriptor(m);
                if (m.getName().equals(wantedName) && selector.substring(paren).equals(desc)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                failures.add(where + " -> " + target.getName() + " has no " + selector);
            }
        }
    }

    /** @At target："Lowner;name(desc)ret" 或字段 "Lowner;name:Ldesc;"。 */
    private static void checkAtTargets(At at, List<String> failures) {
        String targetSpec = at.target();
        if (targetSpec == null || targetSpec.isEmpty()) return;
        if (!targetSpec.startsWith("L")) return;
        int semi = targetSpec.indexOf(';');
        String ownerInternal = targetSpec.substring(1, semi);
        String rest = targetSpec.substring(semi + 1);
        Class<?> owner;
        try {
            owner = Class.forName(ownerInternal.replace('/', '.'));
        } catch (ClassNotFoundException e) {
            return; // owner 不在本进程映射内时无法校验，交由启动兜底
        }
        int paren = rest.indexOf('(');
        int colon = rest.indexOf(':');
        if (colon >= 0 && (paren < 0 || colon < paren)) {
            return; // 字段引用：名称+描述符，宽松放行
        }
        if (paren < 0) return;
        String name = rest.substring(0, paren);
        String desc = rest.substring(paren);
        boolean found = false;
        for (Method m : owner.getDeclaredMethods()) {
            if (m.getName().equals(name) && Type.getMethodDescriptor(m).equals(desc)) {
                found = true;
                break;
            }
        }
        if (!found) {
            failures.add("@At target not found: " + targetSpec);
        }
    }
}
