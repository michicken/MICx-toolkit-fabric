package dev.micx.micxfabric;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 换装是唯一会动 {@code mods/} 的代码，所以这里全部用临时目录跑真实文件操作。
 * 最要紧的两条不变量：**任何时候 mods/ 里都不会同时存在两个本 mod 的 jar**，
 * 以及**凡是认不出来的文件一个字节都不动**。
 */
class UpdateInstallerTest {
    private static final String OLD_JAR = "micx-fabric-0.2.123.jar";
    private static final String NEW_JAR = "micx-fabric-0.2.124.jar";

    @TempDir
    Path root;

    private Path mods;
    private Path staging;

    @BeforeEach
    void setUp() throws IOException {
        mods = Files.createDirectories(root.resolve("mods"));
        staging = Files.createDirectories(root.resolve("update"));
    }

    private Path write(Path dir, String name, String content) throws IOException {
        return Files.writeString(dir.resolve(name), content);
    }

    @Test
    void installsByMovingTheOldJarAside() throws IOException {
        Path current = write(mods, OLD_JAR, "old build");
        Path staged = write(staging, NEW_JAR, "new build");

        UpdateInstaller.Result result = UpdateInstaller.install(
                mods, staged, NEW_JAR, current, "new build".length());

        assertEquals(UpdateInstaller.Result.INSTALLED, result);
        assertTrue(Files.exists(mods.resolve(NEW_JAR)), "新版要在 mods/ 里");
        assertFalse(Files.exists(current), "旧 jar 要让位");
        assertTrue(Files.exists(mods.resolve(OLD_JAR + ".bak")), "旧版留一份可回退");
        assertEquals(1, UpdateInstaller.ownJars(mods).size(), "绝不能同时留着两个 jar");
        assertFalse(Files.exists(staged), "暂存区那份应该被移走");
    }

    @Test
    void refusesToTouchFilesThatAreNotOurs() throws IOException {
        Path foreign = write(mods, "SkillShare-MICx-1.3.0.jar", "someone else");
        Path staged = write(staging, NEW_JAR, "new build");

        UpdateInstaller.Result result = UpdateInstaller.install(
                mods, staged, NEW_JAR, foreign, "new build".length());

        assertEquals(UpdateInstaller.Result.REFUSED, result);
        assertEquals("someone else", Files.readString(foreign), "别人的 jar 一个字节都不能动");
        assertFalse(Files.exists(mods.resolve(NEW_JAR)), "被拒绝时不该把新版放进去");
    }

    @Test
    void refusesAJarNameThatIsNotOurs() throws IOException {
        Path current = write(mods, OLD_JAR, "old build");
        Path staged = write(staging, "evil.jar", "payload");

        UpdateInstaller.Result result = UpdateInstaller.install(mods, staged, "evil.jar", current, 0L);

        assertEquals(UpdateInstaller.Result.REFUSED, result);
        assertTrue(Files.exists(current), "旧 jar 应当原位不动");
    }

    @Test
    void refusesWhenTheStagedSizeDisagreesWithTheManifest() throws IOException {
        Path current = write(mods, OLD_JAR, "old build");
        Path staged = write(staging, NEW_JAR, "new build");

        UpdateInstaller.Result result = UpdateInstaller.install(mods, staged, NEW_JAR, current, 9999L);

        assertEquals(UpdateInstaller.Result.REFUSED, result);
        assertTrue(Files.exists(current));
        assertFalse(Files.exists(mods.resolve(NEW_JAR)));
    }

    @Test
    void anAlreadyInstalledJarIsANoOp() throws IOException {
        Path current = write(mods, OLD_JAR, "old build");
        write(mods, NEW_JAR, "new build");
        Path staged = write(staging, NEW_JAR, "new build");

        assertEquals(UpdateInstaller.Result.ALREADY_STAGED,
                UpdateInstaller.install(mods, staged, NEW_JAR, current, "new build".length()));
        assertTrue(Files.exists(current), "已经装过就别再动旧 jar");
    }

    @Test
    void aSameNamedButDifferentJarIsRefused() throws IOException {
        Path current = write(mods, OLD_JAR, "old build");
        write(mods, NEW_JAR, "something else entirely");
        Path staged = write(staging, NEW_JAR, "new build");

        assertEquals(UpdateInstaller.Result.REFUSED,
                UpdateInstaller.install(mods, staged, NEW_JAR, current, "new build".length()));
        assertTrue(Files.exists(current));
    }

    @Test
    void aMissingStagedJarIsRefused() throws IOException {
        Path current = write(mods, OLD_JAR, "old build");
        assertEquals(UpdateInstaller.Result.REFUSED,
                UpdateInstaller.install(mods, staging.resolve(NEW_JAR), NEW_JAR, current, 0L));
        assertTrue(Files.exists(current));
    }

    @Test
    void thePreviousBackupIsReplacedRatherThanAccumulating() throws IOException {
        write(mods, OLD_JAR + ".bak", "stale backup");
        Path current = write(mods, OLD_JAR, "old build");
        Path staged = write(staging, NEW_JAR, "new build");

        assertEquals(UpdateInstaller.Result.INSTALLED,
                UpdateInstaller.install(mods, staged, NEW_JAR, current, "new build".length()));
        assertEquals("old build", Files.readString(mods.resolve(OLD_JAR + ".bak")));
    }

    @Test
    void ownJarsListsOnlyThisMod() throws IOException {
        write(mods, OLD_JAR, "a");
        write(mods, NEW_JAR + ".bak", "b");
        write(mods, "SkillShare-MICx-1.3.0.jar", "c");
        write(mods, "notes.txt", "d");

        assertEquals(1, UpdateInstaller.ownJars(mods).size());
        assertEquals(OLD_JAR, UpdateInstaller.ownJars(mods).get(0).getFileName().toString());
        assertTrue(UpdateInstaller.ownJars(root.resolve("nope")).isEmpty());
    }

    @Test
    void sha256MatchesTheRealDigest() throws IOException {
        Path file = write(staging, "payload.bin", "hello micx");
        String digest = UpdateInstaller.sha256(file);

        assertTrue(UpdateRules.isSha256(digest));
        assertTrue(UpdateInstaller.matchesSha256(file, digest));
        assertTrue(UpdateInstaller.matchesSha256(file, digest.toUpperCase()), "大小写不敏感");
        assertFalse(UpdateInstaller.matchesSha256(file, "0".repeat(64)));
        assertFalse(UpdateInstaller.matchesSha256(file, "not-a-digest"));
    }
}
