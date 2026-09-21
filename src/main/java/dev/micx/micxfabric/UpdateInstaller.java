package dev.micx.micxfabric;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * jar 换代，以及配套的文件校验。
 *
 * <p>单独成类是为了把「唯一会动 {@code mods/} 的代码」圈在一处，并且可以脱离游戏用临时目录
 * 直接测。这里的纪律：
 * <ul>
 *   <li>只允许碰文件名符合 {@link UpdateRules#isOwnJar} 的文件；</li>
 *   <li>旧 jar 先改名让位、新版才进场；新版进场失败要把旧版改回来；</li>
 *   <li>任何一步不成功都保持「旧的还在原地」——不允许出现两个 jar 同时在 mods/ 里。</li>
 * </ul>
 */
public final class UpdateInstaller {
    /** 换装结果。除了 {@link #INSTALLED}，其它都不算做成事。 */
    public enum Result {
        /** 换好了，重启游戏生效。 */
        INSTALLED,
        /** 新版已经在 mods/ 里了（上次已经装过），不需要再动。 */
        ALREADY_STAGED,
        /** 旧 jar 改不了名——基本只出现在 Windows 上文件被占用的情形。 */
        LOCKED,
        /** 参数不合法或校验不过，一个字节都没改。 */
        REFUSED,
        /** 中途出错，已经尽力把旧版放回原位。 */
        FAILED
    }

    private UpdateInstaller() {
    }

    /**
     * 把已下载并校验好的 jar 装进 mods/。
     *
     * @param modsDir     目标 mods 目录
     * @param stagedJar   暂存区里那份新 jar
     * @param newFileName 新版在 mods/ 里应该叫的名字
     * @param currentJar  正在跑的那个 jar（用 code source 拿到），会被改名让位
     * @param expectedSize 期望字节数，&le;0 表示不校验大小
     */
    public static Result install(Path modsDir, Path stagedJar, String newFileName,
                                 Path currentJar, long expectedSize) {
        if (modsDir == null || stagedJar == null || newFileName == null || currentJar == null) return Result.REFUSED;
        if (!UpdateRules.isOwnJar(newFileName)) return Result.REFUSED;
        Path currentName = currentJar.getFileName();
        if (currentName == null || !UpdateRules.isOwnJar(currentName.toString())) return Result.REFUSED;

        try {
            if (!Files.isRegularFile(stagedJar)) return Result.REFUSED;
            long stagedSize = Files.size(stagedJar);
            if (stagedSize <= 0) return Result.REFUSED;
            if (expectedSize > 0 && stagedSize != expectedSize) return Result.REFUSED;

            Path target = modsDir.resolve(newFileName);
            if (Files.exists(target)) {
                // 已经装过同一份就什么都不用做；同名但内容不同属于意料之外的状态，宁可不碰。
                return Files.mismatch(target, stagedJar) == -1L ? Result.ALREADY_STAGED : Result.REFUSED;
            }

            Path backup = modsDir.resolve(UpdateRules.backupName(currentName.toString()));
            boolean movedAside = false;
            if (Files.exists(currentJar)) {
                try {
                    Files.deleteIfExists(backup);
                    Files.move(currentJar, backup, StandardCopyOption.REPLACE_EXISTING);
                    movedAside = true;
                } catch (IOException locked) {
                    // 旧 jar 让不了位就到此为止：mods/ 里仍然只有原来那一个 jar，可以正常进游戏。
                    MicxFabric.LOGGER.warn("更新：旧 jar 无法改名让位，保持原状（{}）", currentJar, locked);
                    return Result.LOCKED;
                }
            }

            try {
                Files.move(stagedJar, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException failure) {
                if (movedAside) {
                    try {
                        Files.move(backup, currentJar, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException rollbackFailure) {
                        MicxFabric.LOGGER.error("更新：新版进场失败且旧版回滚也失败，mods/ 里现在没有本 mod 的 jar", rollbackFailure);
                    }
                }
                MicxFabric.LOGGER.warn("更新：新版无法写进 mods/", failure);
                return Result.FAILED;
            }
            return Result.INSTALLED;
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("更新：换装过程出错", exception);
            return Result.FAILED;
        }
    }

    /** mods/ 里所有属于本 mod 的 jar（用来发现「同时存在两份」这种要坏的状态）。 */
    public static List<Path> ownJars(Path modsDir) {
        List<Path> found = new ArrayList<>();
        if (modsDir == null || !Files.isDirectory(modsDir)) return found;
        try (var stream = Files.list(modsDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> {
                        Path name = path.getFileName();
                        return name != null && UpdateRules.isOwnJar(name.toString());
                    })
                    .forEach(found::add);
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("更新：列 mods/ 失败", exception);
        }
        return found;
    }

    /** 整个文件的 sha256（小写十六进制）。读不出来返回 null。 */
    public static String sha256(Path file) {
        if (file == null) return null;
        try (InputStream input = Files.newInputStream(file);
             DigestInputStream digest = new DigestInputStream(input, MessageDigest.getInstance("SHA-256"))) {
            byte[] buffer = new byte[8192];
            while (digest.read(buffer) >= 0) {
                // 读完为止；摘要由流自己累积
            }
            return HexFormat.of().formatHex(digest.getMessageDigest().digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            MicxFabric.LOGGER.warn("更新：算 sha256 失败", exception);
            return null;
        }
    }

    /** 文件摘要是否等于期望值（大小写不敏感）。 */
    public static boolean matchesSha256(Path file, String expected) {
        if (!UpdateRules.isSha256(expected)) return false;
        String actual = sha256(file);
        return actual != null && actual.equalsIgnoreCase(expected);
    }
}
